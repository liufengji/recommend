package com.victor.dataloader


import java.net.InetAddress

import com.victor.commons.conf.ConfigurationManager
import org.apache.spark.SparkConf
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient

//mid,name,descri,time,issue,shoot,language,genres,actors,directors
case class Movie(mid:Int,
                 name:String,
                 descri:String,
                 timelong:String,
                 issue:String,
                 shoot:String,
                 language:String,
                 genres:String,
                 actors:String,
                 directors:String)

//mid,name,descri,time,issue,shoot,language,genres,actors,directors
case class MovieWithFlag(mid:Int,
                         name:String,
                         descri:String,
                         timelong:String,
                         issue:String,
                         shoot:String,
                         language:String,
                         genres:String,
                         actors:String,
                         directors:String,
                         flags:String)

//uid,mid,score,timestamp
case class MovieRating(uid:Int,
                       mid:Int,
                       score:Double,
                       timestamp: Long)

//uid,mid,flag,timestamp
case class MovieTag(uid:Int,
                    mid:Int,
                    flag:String,
                    timestamp: Long)

//MySQL的配置
case class MySqlConfig(url:String,
                       username:String,
                       password:String)

//ES的配置
case class ESConfig(httpHosts:String,
                    transportHosts:String,
                    index:String,
                    clustername:String)

/**
  * 用于加载数据
  */
object DataLoader {

  val MOVIES_DATA_FILE = "E:\\workspace\\idea\\jianshu\\RecommendSystem\\heihouzi\\recommend\\recommender\\dataloader\\src\\main\\resources\\small\\movies.csv"
  val RATINGS_DATA_FILE = "E:\\workspace\\idea\\jianshu\\RecommendSystem\\heihouzi\\recommend\\recommender\\dataloader\\src\\main\\resources\\small\\ratings.csv"
  val TAGS_DATA_FILE = "E:\\workspace\\idea\\jianshu\\RecommendSystem\\heihouzi\\recommend\\recommender\\dataloader\\src\\main\\resources\\small\\tags.csv"

  def main(args: Array[String]): Unit = {

    val sparkConf = new SparkConf().setAppName("dataloader").setMaster("local[*]")

    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val sc = spark.sparkContext

    val mySqlConfig = MySqlConfig(ConfigurationManager.config.getString("jdbc.url"),
      ConfigurationManager.config.getString("jdbc.user"),
      ConfigurationManager.config.getString("jdbc.password"))

    //加载数据集
    val moviesRDD = sc.textFile(MOVIES_DATA_FILE)
    val ratingsRDD = sc.textFile(RATINGS_DATA_FILE)
    val tagsRDD = sc.textFile(TAGS_DATA_FILE)

    //将数据集转换为DF
    import spark.implicits._
    val moviesDS = moviesRDD.map{item =>
      val attr = item.split("\\^")
      Movie(attr(0).trim.toInt,
        attr(1).trim,
        attr(2).trim,
        attr(3).trim,
        attr(4).trim,
        attr(5).trim,
        attr(6).trim,
        attr(7).trim,
        attr(8).trim,
        attr(9).trim)
    }.toDS()

    val movieRatings = ratingsRDD.map{item =>
      val attr = item.split(",")
      MovieRating(attr(0).trim.toInt,
        attr(1).trim.toInt,
        attr(2).trim.toDouble,
        attr(3).trim.toLong)
    }.toDS()

    val movieTags = tagsRDD.map{item =>
      val attr = item.split(",")
      MovieTag(attr(0).trim.toInt,
        attr(1).trim.toInt,
        attr(2).trim,
        attr(3).trim.toLong)
    }.toDS()

    moviesDS.cache()
    movieTags.cache()

    //将三个数据集保存到MySQL中
    moviesDS.write
      .format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable","Movies")
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    movieRatings.write
      .format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable","MovieRatings")
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()


    movieTags.write
      .format("jdbc")
      .option("url",mySqlConfig.url)
      .option("dbtable","MovieTags")
      .option("user",mySqlConfig.username)
      .option("password",mySqlConfig.password)
      .mode(SaveMode.Overwrite)
      .save()

    //将Movies数据集和Tags数据集合并
    //mid,name,descri,time,issue,shoot,language,genres,actors,directors
    //uid,mid,flag,timestamp

    val movieFlags = movieTags.rdd.map{item =>
      (item.mid,item.flag)
    }.reduceByKey(_ +"|"+ _)

    val moviesWithFlagsDF = moviesDS.rdd.map{item =>
      (item.mid,item)
    }.leftOuterJoin(movieFlags).map{case (mid,(movie,flags)) =>

      MovieWithFlag(movie.mid,
        movie.name,
        movie.descri,
        movie.timelong,
        movie.issue,
        movie.shoot,
        movie.language,
        movie.genres,
        movie.actors,
        movie.directors,
        flags.getOrElse[String](""))
    }.toDF


    /*es.httpHosts=hadoop102:9200
    es.transportHosts=hadoop102:9300
    es.index.name=recommend
    es.cluster.name=es-cluster*/

    val eSConfig = ESConfig(ConfigurationManager.config.getString("es.httpHosts"),
      ConfigurationManager.config.getString("es.transportHosts"),
      ConfigurationManager.config.getString("es.index.name"),
      ConfigurationManager.config.getString("es.cluster.name"))
    //将数据集保存到ElasticSearch

    val indexname = eSConfig.index

    // 如果ES中存在相同name的index，那么删除
    //设置 cluster.name 属性， 如果不设置，会报节点找到不到
    val setting:Settings  = Settings.builder().put("cluster.name",eSConfig.clustername).build()

    //创建一个es 客户端
    val esClient = new PreBuiltTransportClient(setting)

    //正则表达式  点r  的意思 就是把它当做正则表达式
    val ES_HOST_PORT_REGEX = "(.+):(\\d+)".r

    // hadoop102:9300  InetSocketTransportAddress(InetAddress address, int port)
    eSConfig.transportHosts.split(";")
      .foreach{
        case ES_HOST_PORT_REGEX(host:String,port:String) =>
          //配置 es 客户端
          esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host),port.toInt))
      }

    //esClient.admin().indices()  是发送一个请求的意思
    //什么请求呢？ 判断 indexname 是否存在
    if(esClient.admin().indices().exists(new IndicesExistsRequest(indexname)).actionGet().isExists){
      esClient.admin().indices().delete(new DeleteIndexRequest(indexname)).actionGet()
    }

    esClient.admin().indices().create(new CreateIndexRequest(indexname)).actionGet()

    val movieTypeName = indexname +"/" + "movies"

    val movieOptions = Map("es.nodes" -> eSConfig.httpHosts,
      "es.http.timeout" -> "100m",
      "es.mapping.id" -> "mid"
    )

    moviesWithFlagsDF.write
      .format("org.elasticsearch.spark.sql")
      .options(movieOptions).mode(SaveMode.Overwrite)
      .save(movieTypeName)

    //解除缓存
    moviesDS.unpersist()
    movieTags.unpersist()

    spark.stop()
  }


}
