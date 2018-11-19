

##recommend

  *--dataload  数据灌装
     *--spark -> Mysql
     *--spark -> Elasticsearch
     *--手动 -> redis

  *--statRecommender 统计推荐
     *--spark -> 读取mysql 数据 -> 分析数据 统计指标后 -> 存入mysql
     *--技术点有 spark、UDF函数、开窗函数、Sql炸开

  *--offlineRecommender  离线模型推荐
     *--ALS 模型训练
     *--ALS 参数训练
     *--训练好的用户推荐模型 存入 mysql
     *--训练好的电影推荐模型 存入 mysql 和 redis 

  *--kafkaStream  kafka stream 流数据处理
       KafakaStream 应用

  *--streamingRecommender
      spark streaming  消费 kafka 数据 读取 redis 用户k次 行为和 电影模型数据 
         计算出应该推荐的数据 存入-> mysql

  *--businessServer
       *java spring 框架 做后台服务

  *--website   
     * Angular.js 做前端框架

  *--distribution
     *项目体系打包
    



