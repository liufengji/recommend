package com.victor.commons.pool


import com.victor.commons.conf.ConfigurationManager
import org.apache.commons.pool2.impl.{DefaultPooledObject, GenericObjectPool}
import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}
import redis.clients.jedis.Jedis

class JedisClientFactory(host:String) extends BasePooledObjectFactory[Jedis]{

  override def create(): Jedis = new Jedis(host)

  override def wrap(obj: Jedis): PooledObject[Jedis] = new DefaultPooledObject[Jedis](obj)

}

object PooledJedisClient {

  private var genericObjectPool:GenericObjectPool[Jedis] = null

  def apply(): GenericObjectPool[Jedis] ={
    if(this.genericObjectPool==null){
      this.synchronized{
        val jedisClientFactory = new JedisClientFactory(ConfigurationManager.config.getString("jedis.host"))
        this.genericObjectPool = new GenericObjectPool[Jedis](jedisClientFactory)
      }
    }
    genericObjectPool
  }
}
