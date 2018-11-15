package com.victor.recommender.stat


import com.victor.commons.conf.ConfigurationManager
import com.victor.commons.constant.Constants
import com.victor.commons.model.{Movie, MovieRating, MySqlConfig}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{SaveMode, SparkSession}

// 基于统计的推荐
object StatisticsRecommender {

  def main(args: Array[String]): Unit = {

    //spark 配置
    val sparkConf = new SparkConf().setAppName("statistics").setMaster("local[*]")
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    //mysql 配置参数读取
    val mySqlConfig = MySqlConfig(ConfigurationManager.config.getString(Constants.JDBC_URL),
      ConfigurationManager.config.getString(Constants.JDBC_USER),
      ConfigurationManager.config.getString(Constants.JDBC_PASSWORD))

    //TODO 需求一： 统计电影的平均评分
    import spark.implicits._

    //读取电影评分表数据
    val ratingDS = spark.read
      .format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable",Constants.DB_RATINGS)
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .load()
      .as[MovieRating]

    //缓存电影评分表数据
    ratingDS.cache()

    //电影评分表数据 映射成虚拟表  便于sparksql 查询
    ratingDS.createOrReplaceTempView("ratings")

    //udf函数声明注册  用于sparksql -> sql 语句中使用
    spark.udf.register("halfUp",(num:Double, scale:Int) => {
      val bd = BigDecimal(num)
      bd.setScale(scale, BigDecimal.RoundingMode.HALF_UP).doubleValue()
    })

    //求出 每个电影的平均 评分，并使用udf声明函数 保留 两位小数
    val movieAverageScoreDF = spark.sql("select mid, halfUp(avg(score),2) avg from ratings group by mid")

    //把求出的每个电影的平均分数据写入到mysql 表中
    movieAverageScoreDF
      .write.format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable",Constants.DB_MOVIE_AVERAGE_SCORE)
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    //TODO 需求二 统计优质电影  优质电影 考虑2个方面  评分 大于3.5 分 和 评价 大于 50 次
    val youzhidianyingDF = spark.sql("select " +
      "average.mid, " +
      "halfUp(average.avg,2) avg, " +
      "average.count, " +
      "row_number() over(order by average.avg desc,average.count desc) rank " +
      " from " +
      " (select mid, avg(score) avg, count(*) count from ratings group by  mid) average where avg > 3.5 and count > 50")

    //将求出的最优电影存到mysql 数据库中
    youzhidianyingDF
      .write.format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable",Constants.DB_MOVIE_YOUZHI)
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    //TODO 需求三 统计最热电影
    //统计最热电影，主要考虑 评价次数最多  和   时间两个方面，时间上 取最后一次评价的时间 减去 30 天的时间  作为 最近时间的范围
    //val maxtimeDF = spark.sql("select max(timestamp)-7776000 max from ratings")
    val hotMovies = spark.sql("select " +
      "mid, " +
      "count(*) count  " +
      "from  ratings " +
      "where timestamp > (select max(timestamp)-7776000 max from ratings) " +
      "group by mid " +
      "order by count desc " +
      "limit 10")

    //将最热门数据存储到mysql 数据库中
    hotMovies
      .write.format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable",Constants.DB_MOVIE_HOT)
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    //TODO 需求四 电影类别的Top10 电影统计
    //数据映射成虚拟表
    movieAverageScoreDF.createOrReplaceTempView("averageMovies")

    //import org.apache.spark.sql.functions._

    //读取电影表数据
    val moviesDS = spark.read
      .format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable",Constants.DB_MOVIES)
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .load()
      .as[Movie]

    //把读取的数据映射成虚拟表
    moviesDS.createOrReplaceTempView("movies")

    //电影表 和 每个电影的平均评分表  数据 合并
    val movieWithScore = spark.sql("select a.mid, a.genres, if(isnull(b.avg),0,b.avg) score from movies a left join averageMovies b on a.mid = b.mid")

    //声明udf函数 用于String 字符串切分
    spark.udf.register("splitGe",(genres:String) => {
      genres.split("\\|")
    })

    //数据映射成虚拟表
    movieWithScore.createOrReplaceTempView("movieWithScore")

    //求出 每个类型的电影排名前十的，spark sql 语句中 使用了
    //row_number() over(partition by gen order by score desc) rank  ， row_number 排名
    //explode(splitGe(genres)) gen   ，explode 炸开  等技术
    val genresTop10Movies = spark.sql("select * from (select " +
      "mid," +
      "gen," +
      "score, " +
      "row_number() over(partition by gen order by score desc) rank " +
      "from " +
      "(select mid,score,explode(splitGe(genres)) gen from movieWithScore) genresMovies) rankGenresMovies " +
      "where rank <= 10")

    //将每个类型的电影排名前十的 电影数据写入到mysql 数据库中
    genresTop10Movies.write.format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable",Constants.DB_GENRES_TOP_MOVIES)
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()


    //解除 cache 缓存
    ratingDS.unpersist()

    //关闭spark
    spark.stop()
  }

}
