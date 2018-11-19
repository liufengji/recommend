package com.victor.offline

import breeze.numerics.sqrt
import com.victor.commons.conf.ConfigurationManager
import com.victor.commons.constant.Constants
import com.victor.commons.model.{MovieRating, MySqlConfig}
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

//RMSE  均方根误差
object ALSTrainerApp {

  def main(args: Array[String]): Unit = {

    //TODO ALS 模型在训练的时候，RMSE 怎么计算？ 使用均方根误差，ALS 模型参数确定的一种手段（）
    //寻找查准率，查全率 方差
    /* 1、训练集、测试集
     1、使用训练集进行ALS模型的训练  =》 model
     2、将训练集中的所有真实评分去掉，  使用model去进行预测 产生预测集
     3、通过RMSE(均方根误差)公式计算  预测集和训练集之间的误差*/
    val sparkConf = new SparkConf().setAppName("trainer").setMaster("local[*]")
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val sc = spark.sparkContext

    val mySqlConfig = MySqlConfig(ConfigurationManager.config.getString("jdbc.url"),
      ConfigurationManager.config.getString("jdbc.user"),
      ConfigurationManager.config.getString("jdbc.password"))

    import spark.implicits._
    val ratingDS = spark.read
      .format("jdbc")
      .option("url", mySqlConfig.url)
      .option("dbtable", Constants.DB_RATINGS)
      .option("user", mySqlConfig.username)
      .option("password", mySqlConfig.password)
      .load()
      .as[MovieRating]

    val trainData = ratingDS.rdd.map {
      item => Rating(item.uid, item.mid, item.score)
    }

    ratingDS.cache()
    trainData.cache()

    //返回均方根误差
    def computeRmse(model: MatrixFactorizationModel, trainData: RDD[Rating]): Double = {

      val user2product = trainData.map { item =>
        (item.user, item.product)
      }

      val predictRating = model.predict(user2product)

      val up2Predict = predictRating.map(item => ((item.user, item.product), item.rating))

      val up2Real = trainData.map(item => ((item.user, item.product), item.rating))

      val real2predict = up2Predict.join(up2Real)

      //sqrt()  方法 是用来开方的
      //mean() 方法 是用来 求均值的
      //均方根误差
      sqrt(real2predict.map { case ((uid, pid), (redict, real)) =>
        val err = redict - real
        err * err
      }.mean())

    }


    //设置你的参数范围  foreach
    val evaluations =
      for (rank <- Array(20, 50); lamba <- Array(1.0, 0.0001)) yield {
        //(ratings: RDD[Rating], rank: Int, iterations: Int, lambda: Double, alpha: Double)
        //Rating,特征，迭代训练次数，预值
        val model = ALS.train(trainData, rank, 10, lamba)

        //返回均方根误差
        val rmse = computeRmse(model, trainData)

        ((rank, lamba), rmse)
      }

    //基于你的参数训练model
    //找到RMSE中最小的输出 参数组合
    for (item <- evaluations) {
      println(s"参数组合： rank -> ${item._1._1}  lamba -> ${item._1._2}  RMSE: -> ${item._2}")
    }

    spark.stop()
  }

}

