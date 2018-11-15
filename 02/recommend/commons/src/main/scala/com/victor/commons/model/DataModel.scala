/*
 * Copyright (c) 2017. Atguigu Inc. All Rights Reserved.
 * Date: 10/31/17 5:07 PM.
 * Author: wuyufei.
 */

package com.victor.commons.model

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

case class Recommend(id:Int,
                     recs:String)
