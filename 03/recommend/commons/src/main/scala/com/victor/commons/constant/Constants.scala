/*
 * Copyright (c) 2017. Atguigu Inc. All Rights Reserved.
 * Date: 10/26/17 11:24 PM.
 * Author: wuyufei.
 */

package com.victor.commons.constant

/**
 * 常量接口
 * @author wuyufei
 *
 */
object Constants {

	/**
		* 项目配置相关的常量
		*/
	val JDBC_DATASOURCE_SIZE = "jdbc.datasource.size"
	val JDBC_URL = "jdbc.url"
	val JDBC_USER = "jdbc.user"
	val JDBC_PASSWORD = "jdbc.password"


	val KAFKA_TOPICS = "kafka.topics"


	// MySQL相关表
	val DB_MOVIES = "movies"  //电影
	val DB_RATINGS = "movie_ratings" //电影评价评分
	val DB_TAGS = "movie_tags"   //电影标签分类

	val DB_MOVIE_AVERAGE_SCORE = "movie_average_score"   //电影平均得分统计
	val DB_MOVIE_YOUZHI = "movie_youzhi"  //优质电影
	val DB_MOVIE_HOT = "movie_hot"        //最热电影
	val DB_GENRES_TOP_MOVIES = "genres_top_movies"   //每个类别优质电影统计

	val DB_USER_RECS = "user_recs"  //用户电影推荐矩阵
	val DB_MOVIE_RECS = "movie_recs"  //电影相似度矩阵

}