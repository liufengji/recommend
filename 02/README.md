

##recommend

  --dataload  数据灌装
     --spark -> Mysql
     --spark -> Elasticsearch

  --statRecommender 统计推荐
     --spark -> 读取mysql 数据 -> 分析数据 统计指标后 -> 存入mysql
     --技术点有 spark、UDF函数、开窗函数、Sql炸开

  --offlineRecommender  离线模型推荐
     --ALS 模型训练
     --ALS 参数训练
     --训练好的用户推荐模型 存入 mysql
     --训练好的电影推荐模型 存入 mysql 和 redis 


