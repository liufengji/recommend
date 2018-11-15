package com.victor.offline


import com.victor.commons.conf.ConfigurationManager
import com.victor.commons.constant.Constants
import com.victor.commons.model._
import com.victor.commons.pool.PooledJedisClient
import com.victor.commons.utils.NumberUtils
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.jblas.DoubleMatrix

import scala.collection.JavaConversions._

//基于ALS模型的离线推荐
object OfflineRecommender {

  def main(args: Array[String]): Unit = {

    //Run ->Edit Configurations ->  VM options:-Xss3069k   栈溢出问题解决  参数设置大一点

    //spark 配置
    val sparkConf = new SparkConf().setAppName("als").setMaster("local[*]")
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val sc = spark.sparkContext

    //mysql  配置
    val mySqlConfig = MySqlConfig(ConfigurationManager.config.getString("jdbc.url"),
      ConfigurationManager.config.getString("jdbc.user"),
      ConfigurationManager.config.getString("jdbc.password"))

    //训练模型
    import spark.implicits._

    //读取电影表数据
    val movieDS = spark.read
      .format("jdbc")
      .option("url", mySqlConfig.url)
      .option("dbtable", Constants.DB_MOVIES)
      .option("user", mySqlConfig.username)
      .option("password", mySqlConfig.password)
      .load()
      .as[Movie]

    // 读取电影评分表数据
    val ratingDS = spark.read
      .format("jdbc")
      .option("url", mySqlConfig.url)
      .option("dbtable", Constants.DB_RATINGS)
      .option("user", mySqlConfig.username)
      .option("password", mySqlConfig.password)
      .load()
      .as[MovieRating]

    //读取电影分类表数据
    val tagDS = spark.read
      .format("jdbc")
      .option("url", mySqlConfig.url)
      .option("dbtable", Constants.DB_TAGS)
      .option("user", mySqlConfig.username)
      .option("password", mySqlConfig.password)
      .load()
      .as[MovieTag]

    //ALS模型 参数1  ,根据电影评分表数据，先把Rating做出来
    val trainData = ratingDS.rdd.map { item =>
      Rating(item.uid, item.mid, item.score)
    }

    //将电影表、评分表、分类表 添加到cache缓存中
    movieDS.cache()
    ratingDS.cache()
    tagDS.cache()

    //将 ALS 模型 参数 Rating 添加到cache缓存中
    trainData.cache()

    // def train(ratings: RDD[Rating], rank: Int, iterations: Int, lambda: Double)
    //           trainData训练数据             几个特征         迭代次数      预值
    //TODO 需求一： 训练模型完成
    val model = ALS.train(trainData, 50, 20, 0.01)

    //TODO 需求二：用户电影推荐矩阵
    //根据模型计算用户推荐矩阵  RDD[(uid,Seq[(mid,score)])]  50个
    //distinct  去除重复数据
    //获取电影标，电影id数据并去重
    val midRDD = movieDS.rdd.map(_.mid).distinct()
    //获取电影评分数据和电影标签数据的 用户id并去重
    val ratingUidRDD = ratingDS.rdd.map(_.uid).distinct()
    val tagUidRDD = tagDS.rdd.map(_.uid).distinct()

    //电影评分用户id和电影标签用户id 合并之后去重
    val uidRDD = ratingUidRDD.union(tagUidRDD).distinct()

    //cartesian 是 笛卡尔积 方法，返回一个元组（uid,mid）
    val uid2mid = uidRDD.cartesian(midRDD)

    //根据训练好的模型，放入 uid 和 mid 元组 合并数据 ，预测 什么用户喜欢什么样的电影
    //预测用户喜欢啥
    val predictRatings = model.predict(uid2mid)

    //Rating -> 元组 -> 数据过滤 条件 评分大于3.5 分 -> groupbykey 根据用户uid 分组 -> map -> 样例类 ->toDF
    //predictRatingscase  user: Int, product: Int, rating: Double
    val userRecsDF = predictRatings.map { item =>
      (item.user, (item.rating, item.product))
    }.filter(_._2._1 > 3.5)
      .groupByKey()
      .map { case (uid, items) =>
        Recommend(uid,
          //items -> sortby排序 -> reverse 反转从大到小 -> 取前50个数据 -> Map -> 数据处理 -> String  一个Key 多个value value用| 隔开
          // 使用了 NumberUtils.formatDouble(item._1,2) 自定义函数 -> Double 保留了2位小数
          items.toList.sortBy(_._1).reverse.take(50).map(item => item._2 + ":" + NumberUtils.formatDouble(item._1, 2)).mkString("|"))
      }.toDF()

    // Recommend(uid,(mid:4.5|mid:5.0))
    //用户 喜欢的 电影和评分 存入mysql 数据库
    //用户电影推荐矩阵 存入mysql数据库
    userRecsDF
      .write
      .format("jdbc")
      .option("url", mySqlConfig.url)
      .option("dbtable", Constants.DB_USER_RECS)
      .option("user", mySqlConfig.username)
      .option("password", mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    //TODO 需求三 求电影的相似度矩阵  最短距离-余弦相似度的方式
    //计算电影的相似度矩阵
    //电影的特征矩阵 model.productFeatures
    val productFeaturesRDD = model.productFeatures.map { case (mid, feature) =>
      val factorVector = new DoubleMatrix(feature)
      (mid, factorVector)
    }

    //它的内积 除以  模长的 成绩  求余弦相似度
    // f1.dot(f2)  两个向量的内积
    //  f1.norm2()  两个向量的莫
    def consinSim(f1: DoubleMatrix, f2: DoubleMatrix): Double = {
      f1.dot(f2) / (f1.norm2() * f2.norm2())
    }

    //productFeaturesRDD -> cartesian 是 笛卡尔积 方法，返回一个元组 -> filter过滤 -> map -> consinSim余弦相似度
    //电影和电影之间的相似度 ,电影相似度矩阵
    val movieSim = productFeaturesRDD.cartesian(productFeaturesRDD).filter { case (itemA, itemB) => itemA._1 != itemB._1 }
      .map { case ((mid1, feature1), (mid2, feature2)) =>
        (mid1, (mid2, consinSim(feature1, feature2)))
      }.filter(_._2._2 > 0.6)
      .groupByKey()
      .map { case (mid, items) =>

        // 创建 redis 连接池
        val redisPool = PooledJedisClient()

        //创建一个 redis 对象
        val redis = redisPool.borrowObject()

        //将最相似的50个电影保存到Redis中的Set结构
        redis.del("set:" + mid)

        //:_* 语法  是把数组设置成可变参数
        redis.sadd("set:" + mid, items.toSeq.sortBy(_._2).reverse.take(50).map(item => item._1 + ":" + item._2): _*)

        //将当前电影的相似度矩阵保存成Map结构
        redis.del("map:" + mid)
        redis.hmset("map:" + mid, items.toSeq.map(item => (item._1.toString, item._2.toString)).toMap)

        //关闭redis连接池
        redisPool.returnObject(redis)

        //通过样例类 数据写入到对象中, 一个key,有多个value
        Recommend(mid, items.map(item => item._1 + ":" + NumberUtils.formatDouble(item._2, 2)).mkString("|"))
      }.toDF()


    //写入到mysql数据库
    movieSim
      .write
      .format("jdbc")
      .option("url", mySqlConfig.url)
      .option("dbtable", Constants.DB_MOVIE_RECS)
      .option("user", mySqlConfig.username)
      .option("password", mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    //清除cache缓存数据
    trainData.unpersist()
    ratingDS.unpersist()
    movieDS.unpersist()
    tagDS.unpersist()

    spark.stop()
  }

}

