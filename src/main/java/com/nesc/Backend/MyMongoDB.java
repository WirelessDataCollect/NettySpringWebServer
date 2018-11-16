package com.nesc.Backend;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteConcernException;
import com.mongodb.MongoWriteException;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
//import com.mongodb.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
/**
* 
* MongoDB数据库类
*
* @author  nesc418
* @Date    2018-10-28
* @version 0.1.1
*/
public class MyMongoDB{
	public MongoCollection<Document> collection;
	protected MongoClient mongoClient;
	protected MongoDatabase mongoDatabase;
	private String colName;
	private String dbName;
	/**
	 * num 从mongodb中获取到的doc个数
	 */
	volatile Long docNum=(long) -1;
	/**
	 * MongoDB数据库的集合选择方法
	 * @param colName
	 */
	public void setColName(String colName) {
		this.colName = colName;
	}
	/**
	 * 获取对象操作的集合
	 * @return colName：本次操作集合名称
	 */
	public String getColname() {
		return colName;
	}
	/**
	* MongoDB数据库类的设置函数
	*
	* @param dbName 数据库名称
	* @return 无
	* @throws 无
	*/
	public void setDbName(String dbName) {
		this.dbName = dbName;  	
	}
	/**
	 * 获取对象连接的数据库
	 * @return dbName：本次操作
	 */
	public String getDbName() {
		return dbName;
	}

	/**
	 * 准备操作一个当前的db
	 * @param dbName 想要操作的数据库名称
	 * @return {@link MongoDatabase} 数据库
	 * @throws IllegalArgumentException
	 */
	public MongoDatabase getDb(String dbName) throws IllegalArgumentException{
		this.dbName = dbName;
		try {
			return this.mongoClient.getDatabase(this.dbName);
		}catch(IllegalArgumentException e) {
			throw e;
		}
	}
	/**
	 * 重新设置和连接要操作的数据库
	 * @param dName
	 */
	public synchronized void resetDb(String dName) {
		/* 连接到 mongodb 服务*/
		try {
//			mongoClient = MongoClients.create(); //不同于init，这里不需要再连接mongodb了
			this.setDbName(dName);//重新设置col名称
			mongoDatabase = mongoClient.getDatabase(this.dbName);
			System.out.printf("Connected to db(%s) successfully",this.dbName);	
		}catch(Exception e) {
			//连接失败的err警告   
			System.err.printf("Connected to db(%s) unsuccessfully!",this.dbName);
			//连接失败后，输出当前的连接参数
			System.err.printf("\tNow params this.dbName,this.colName is %s , %s ",this.dbName,this.colName);
		}		
	}
	/**
	 * 准备操作当前的collection
	 * @param colName 想要操作的数据库集合名称
	 * @return {@link MongoCollection} 数据库集合
	 * @throws IllegalArgumentException
	 */
	public MongoCollection<Document> getCol(String colName) throws IllegalArgumentException{
		this.colName = colName;
		try {
			return this.mongoDatabase.getCollection(this.colName);
		}catch(IllegalArgumentException e) {
			throw e;
		}
	}
	/**
	 * 重新设置要操作的集合
	 * @param cName
	 */
	public synchronized void resetCol(String cName) {
		/* 连接到 mongodb 服务*/
		try {
//			mongoClient = MongoClients.create(); //不同于init，这里不需要再连接mongodb了
			this.setColName(cName);//重新设置col名称
			collection = mongoDatabase.getCollection(this.colName);
			System.out.printf("Connected to db.col(%s.%s) successfully",this.dbName,this.colName);	
		}catch(Exception e) {
			//连接失败的err警告
			System.err.printf("Connected to db.col(%s.%s) unsuccessfully!",this.dbName,cName);
			//连接失败后，输出当前的连接参数
			System.err.printf("\tNow params this.dbName,this.colName is %s , %s ",this.dbName,this.colName);
		}		
	}
	/**
	 * mongodb初始化函数，连接mongodb
	 */
	@PostConstruct   //初始化回调函数
	public synchronized void init() {
		/* 连接到 mongodb 服务*/
		try {
			mongoClient = MongoClients.create();
			mongoDatabase = mongoClient.getDatabase(this.dbName);
			collection = mongoDatabase.getCollection(this.colName);
			System.out.printf("Connected to db.col(%s.%s) successfully\n",this.dbName,this.colName);	
		}catch(Exception e) {
			System.err.println("MongoDB init unsuccessfully!");
		}
	}
	/**
	 * 在集合中插入一个doc
	 * @param document
	 * @param callback
	 */
	public synchronized void insertOne(Document document, SingleResultCallback<Void> callback) 
			throws MongoWriteException,MongoWriteConcernException,MongoException{
		try{
			this.collection.insertOne(document, callback);
		}catch(MongoWriteException e) {
			throw e;
		}catch(MongoWriteConcernException e) {
			throw e;
		}catch(MongoException e) {
			throw e;
		}
	}
	/**
	 * 查找满足要求的doc有几个
	 * @param filter 过滤条件
	 * @return {@link FindIterable} /null
	 */
	public Long count(Bson filter) {
		try {
			SingleResultCallback<Long> callback = new SingleResultCallback<Long>() {
	            @Override
	            public void onResult(final Long result, final Throwable t) {
	            	docNum = result;
			        System.out.println("Find "+result.toString());
			    }};
			this.collection.countDocuments(filter, callback);
			//等待改变
			while(docNum<0) {
			}
			Long temp = docNum;
			docNum = (long) -1;
			return temp;
		}catch(Exception e){
			e.printStackTrace();
		}
		return (long) 0;
	}
	/**
	 * 过滤并找到doc
	 * @param filter 过滤条件
	 * @return {@link FindIterable} /null
	 */
	public FindIterable<Document> find(Bson filter) {
		try {
			return this.collection.find(filter);
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 找信息
	 * @return {@link FindIterable} /null
	 */
	public FindIterable<Document> find() {
		try {
			return this.collection.find();
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 断开和mongodb的连接
	 */
	@PreDestroy    //销毁回调函数
	public void destroy() {
		mongoClient.close();
	}
}
