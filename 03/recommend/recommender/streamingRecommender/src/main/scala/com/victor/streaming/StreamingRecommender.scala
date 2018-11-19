package com.victor.streaming


import java.sql.ResultSet

import com.victor.commons.conf.ConfigurationManager
import com.victor.commons.pool.{CreateMySqlPool, PooledJedisClient, QueryCallback}
import kafka.serializer.StringDecoder
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka.KafkaUtils

import scala.collection.JavaConversions._

object StreamingRecommender {

  def main(args: Array[String]): Unit = {

    val sparkConf = new SparkConf().setAppName("streaming").setMaster("local[*]")

    val sc = new SparkContext(sparkConf)

    val ssc = new StreamingContext(sc,Seconds(5))

    val broker = ConfigurationManager.config.getString("kafka.broker.list")
    val topic = "log"

    //连接到Kafka
    val kafkaParams = Map[String,String](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> broker,
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG ->"largest",
      ConsumerConfig.GROUP_ID_CONFIG -> "recommender"
    )

    val movieScoreDStream = KafkaUtils.createDirectStream[String,String,StringDecoder,StringDecoder](ssc,kafkaParams,Set(topic))

    //key是什么鬼？？ key 是 null
    //788,2318,4.0,1298861753   uid ,mid score ,timestamp
    val scoreDStream = movieScoreDStream.map{ case (key,value) =>
      val attr = value.split(",")
      (attr(0).trim.toInt, attr(1).trim.toInt, attr(2).trim.toDouble, attr(3).trim.toLong)
    }

    scoreDStream.foreachRDD{ rdd =>

      rdd.map{ case (uid,mid,score,timestamp) =>

        println(s"${uid}")

        // 获取所有的备选电影(redis中拿)
        val redisPool = PooledJedisClient()
        val redis = redisPool.borrowObject()

        val mysqlPool = CreateMySqlPool()
        val mysql = mysqlPool.borrowObject()

        //备选电影  6197:0.911710497657166    目前最多50个   mid:相似度
        val beiXuanMovies = redis.smembers("set:"+mid)

        // 获取当前用户的K次评分   1378:4.0   mid:score
        //uid:788
        val recentlyKScores = redis.lrange("uid:"+uid, 0, 20)

        //将设n为3 ， 以2为底 三的对数
        def logFunc(n:Int): Double ={
          if(n > 0 ){
            math.log(n) / math.log(2)
          }else
            0
        }

        // foreach 每一个备选电影  计算推荐优先级
        //  返回 电影mid 的 推荐指数
        val recsPriority = for (movie <- beiXuanMovies) yield {
          val bmid = movie.split(":")(0)
          val bscore = movie.split(":")(1)

          var sum = 0
          var score = 0D
          //偏离
          var incount = 0
          var recount = 0

          //获取当前mid的备选电影相似度矩阵  HashMap
          val similarMap = redis.hgetAll("map:"+bmid)

          //遍历用户的评分次数 mid:score
          for (rating <- recentlyKScores){
            val kmid = rating.split(":")(0)
            val kscore = rating.split(":")(1).toDouble

            //查询相似度？ 如果没查到 相似度为0
            val sim = similarMap.getOrDefault(kmid,"0").toDouble
            if(sim != 0){
              sum += 1
              score += sim * kscore
            }
            // 如果这两个电影的相似度 》 0.7  考虑偏移值 的问题
            if(sim > 0.7){
              if(kscore > 3)
                incount += 1
              else
                recount += 1
            }
          }

          (bmid.toInt, score / sum  + logFunc(incount)  - logFunc(recount))
        }

        // 所有备选电影的推荐优先级

        //获取数据库所有数据
        var oldStreamRecs = ""

        // 合并数据库中的数据  [时间的惩罚因子]
        mysql.executeQuery("select * from streamrecs where uid="+uid, null, new QueryCallback {
          override def process(rs: ResultSet): Unit = {
            if(rs.next()){
              oldStreamRecs = rs.getString(2)
            }
          }
        })

        val oldRecs = if(oldStreamRecs != ""){
          oldStreamRecs.split("\\|").map{ item =>
            val attr = item.split(":")
            (attr(0).toInt, attr(1).toDouble)
          }.toArray
        }else{
          Array[(Int,Double)]()
        }

        val unoinRecs = recsPriority.toArray.union(oldRecs)

        val newRecs = unoinRecs.toList.sortBy(_._2).reverse.take(10).map(item => item._1 + ":" + item._2).mkString("|")

        // 说明没有 直接插入
        if(oldStreamRecs==""){
          val para:Array[Any] = Array(uid,newRecs)
          mysql.executeUpdate("insert into streamrecs values (?,?)", para)
        }else{
          //update更新
          val para:Array[Any] = Array(newRecs)
          mysql.executeUpdate("update streamrecs set recs=? where uid="+uid, para)
        }

        mysqlPool.returnObject(mysql)
        redisPool.returnObject(redis)
      }.count()
    }

    ssc.start()
    ssc.awaitTermination()

  }
}
