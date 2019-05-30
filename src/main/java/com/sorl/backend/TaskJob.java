package com.sorl.backend;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.ClientSession;
import com.mongodb.async.client.FindIterable;
import com.mongodb.async.client.MongoIterable;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;

/**
* 
* 部分周期性执行任务
*
* @author  nesc420
* @Date    2019-5-7
* @version 0.1.0
*/
@Component("taskJob")
public class TaskJob{
	private final static Logger logger = Logger.getLogger(TaskJob.class);
	//清除60天前的数据
	private final static int DAYS_BEFORE_TODAY = -60;
	//配置任务的执行时间，可以配置多个
	private final static String hms4MgdClearByIsodate = "T04:00:00";
	
	/**
	 * 更新用于插入数据的MongoClient所指向的集合
	 * @param 无
	 * @throws ParseException 
	 */
	@Scheduled(cron="0 0 0 1 * ?") 
	public void dataMgdUpdate() throws ParseException {
		logger.info("Start updating data mongoclient's collection...");
		MyMongoDB dataMgd = (MyMongoDB)App.getApplicationContext().getBean("myMongoDB");
		dataMgd.resetCol(TimeUtils.getStrIsoMTime());
		//建立一个索引
		if(!dataMgd.getIndexName().equals("")) {
			dataMgd.collection.createIndex(Indexes.descending(dataMgd.getIndexName()), new SingleResultCallback<String>() {
				@Override
				public void onResult(final String result, final Throwable t) {
					logger.info(String.format("db.col create index by \"%s\"(indexName_-1)", result));
				}
			});
		}
		logger.info("Updated data mongoclient's collection successfully");
	}
	/**
	 * 基于从testName提取出来的isodate为基础，进行数据清除
	 * 
	 * @note 使用isodate而不使用insertIsodate的原因：
	 * 如果私有云数据库本身系统时间有问题，可能会造成把所有数据都删除的问题。
	 * 而isodate从配置文件或者是数据中提取，删除也只是会删除那一次的配置文件或者数据
	 * @return none
	 */
	//取消以下注释，则周期性运行
//	@Scheduled(cron="0 0 4 * * ?")  //凌晨4点执行数据库清空指令（DAYS_BEFORE_TODAY天之前的数据）
	public void mgdClearByIsodate() {
		try {
			logger.info("mgdClearByInsertIsodate Start Clearing N-day-before datas and configurations");
			MyMongoDB generalMgdInterface = (MyMongoDB)App.getApplicationContext().getBean("generalMgdInterface");
			MyMongoDB testInfoMongdb = (MyMongoDB)App.getApplicationContext().getBean("testConfMongoDB");
		
			generalMgdInterface.getClient().startSession(new SingleResultCallback<ClientSession>() {
				@Override
				public void onResult(final ClientSession sess, final Throwable t) {
					if(t != null) {
						logger.warn("StartSession Throwable is not null",t);
					}
					//如果不支持事务，则不开启
					if(sess != null) {
						sess.startTransaction();
					}
					try {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
						//今日日期
						Calendar calendar=new GregorianCalendar();
						logger.info(String.format("Date today : " + sdf.format(calendar.getTime()) + TaskJob.hms4MgdClearByIsodate));
						//N天前的日期
						calendar.add(Calendar.DATE, TaskJob.DAYS_BEFORE_TODAY); 
						String upperBound = sdf.format(calendar.getTime()) + TaskJob.hms4MgdClearByIsodate;
						logger.info(String.format("Date before N days : " + upperBound));
						//基于插入时间（系统时间，所以系统时间不能错）
						BasicDBObject filter = new BasicDBObject();
						filter.put(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_ISODATE, new BasicDBObject("$lte",upperBound));
						// 返回的document包含那些内容，后面只有testname需要
						BasicDBObject projections = new BasicDBObject();
						projections.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME, 1)
									.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA, 1)
									.append("_id", 0);
						//设置指向配置文件的col
						generalMgdInterface.resetCol(testInfoMongdb.getColName());
						FindIterable<Document> findIter = generalMgdInterface.collection.find(filter).projection(projections)
								.sort(new BasicDBObject(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA, 1));
						findIter.forEach(new Block<Document>() {
							@Override
							public void apply(Document doc) {
								try {
									//指向数据集合
									if(!((String)doc.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA)).equals(generalMgdInterface.getColName())) {
										generalMgdInterface.resetCol((String)doc.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_COL_INDEX_OF_DATA));
									}
									logger.info(String.format("Connected to db.col(%s.%s) ", generalMgdInterface.getDbName(),generalMgdInterface.getColName()));
									String testName = (String)doc.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME);
									//删除ADC和CAN数据
									generalMgdInterface.collection.deleteMany(new BasicDBObject(DataProcessor.MONGODB_KEY_TESTNAME,testName), new SingleResultCallback<DeleteResult>() {
										@Override
										public void onResult(final DeleteResult result, final Throwable t) {
											logger.info(String.format("Cleared %d documents", result.getDeletedCount()));
										}	
						        	});
								}catch(Exception e) {
									logger.info("",e);
								}
							}
			        	},  new SingleResultCallback<Void>() {
							@Override
							public void onResult(final Void result, final Throwable t) {
								 //删除config数据，需要改变generalMgdInterface，所以必须在上一步结束后进行
								 generalMgdInterface.resetCol(testInfoMongdb.getColName());
								 generalMgdInterface.collection.deleteMany(filter, new SingleResultCallback<DeleteResult>() {
									@Override
									public void onResult(final DeleteResult result, final Throwable t) {
										logger.info(String.format("Cleared %d configurations", result.getDeletedCount()));
									}
					        	});
							}
			        	});
						
						if(sess != null) {
							sess.commitTransaction(new SingleResultCallback<Void>() {
								@Override
								public void onResult(final Void result, final Throwable t) {
								}	
				        	});
						}
					}catch(Exception e) {
						logger.error("",e);
						if(sess != null) {
							sess.abortTransaction(new SingleResultCallback<Void>() {
								@Override
								public void onResult(final Void result, final Throwable t) {
								}	
				        	});
						}
					}
				}	
        	});
		}catch (Exception e) {
			logger.error("",e);
		} 
	}
	
	
	/*
	 * 删除，分别从config和data集合删除数据，没有加速措施
	 */
	//配置任务的执行时间，可以配置多个
	private final static String hms4MgdClearByInsertIsodate = "T03:00:00";
	//取消以下注释，则周期性运行
//	@Scheduled(cron="0 0 3 1 * ?")  //每个月1号凌晨3点清除一次
	public void mgdClearByInsertIsodateSeperate() {
		try {
			logger.info("mgdClearByInsertIsodate Start Clearing N-day-before documents and configurations");
			MyMongoDB generalMgdInterface = (MyMongoDB)App.getApplicationContext().getBean("generalMgdInterface");
			
			generalMgdInterface.getClient().startSession(new SingleResultCallback<ClientSession>() {
				@Override
				public void onResult(final ClientSession sess, final Throwable t) {
					if(t != null) {
						logger.warn("StartSession Throwable is not null",t);
					}
					//如果不支持事务，则不开启
					if(sess != null) {
						sess.startTransaction();
					}
					try {
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
						//今日日期
						Calendar calendar = new GregorianCalendar();
						logger.info(String.format("Date today ： " + sdf.format(calendar.getTime()) + TaskJob.hms4MgdClearByInsertIsodate));
						//N天前的日期
						calendar.add(Calendar.DATE, TaskJob.DAYS_BEFORE_TODAY); 
						String upperBound = sdf.format(calendar.getTime()) + TaskJob.hms4MgdClearByInsertIsodate;
						logger.info(String.format("Date before N days ： " + upperBound));
						BasicDBObject filter = new BasicDBObject();
						MongoIterable<String> colNameList = generalMgdInterface.getDb().listCollectionNames();
						colNameList.forEach(new Block<String>() {
							@Override
							public void apply(String colName) {
								try {
									//设置指向集合
									generalMgdInterface.resetCol(colName);
									if(colName.equals("config")) {
										//testInfoMgd的dataMgd的插入文档时间字段相同，均为insertIsodate
										filter.put(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_INSERT_ISO_DATE, new BasicDBObject("$lte",upperBound));
									}else {
										filter.put(DataProcessor.MONGODB_KEY_INSERT_ISO_DATE, new BasicDBObject("$lte",upperBound));
									}
									generalMgdInterface.collection.deleteMany(filter, new SingleResultCallback<DeleteResult>() {
										@Override
										public void onResult(final DeleteResult result, final Throwable t) {
											if(colName.equals("config")) {
												logger.info(String.format("Cleared %d configurations", result.getDeletedCount()));
											}else {
												logger.info(String.format("Cleared %d documents", result.getDeletedCount()));
											}
											
										}
						        	});
								}catch(Exception e) {
									logger.info("",e);
								}
							}
			        	},  new SingleResultCallback<Void>() {
							@Override
							public void onResult(final Void result, final Throwable t) {
								logger.info("mgdClearByInsertIsodate Cleared N-day-before documents and configurations Over");
							}
			        	});
						if(sess != null) {
							sess.commitTransaction(new SingleResultCallback<Void>() {
								@Override
								public void onResult(final Void result, final Throwable t) {
								}	
				        	});
						}
					}catch(Exception e) {
						logger.error("",e);
						if(sess != null) {
							sess.abortTransaction(new SingleResultCallback<Void>() {
								@Override
								public void onResult(final Void result, final Throwable t) {
								}	
				        	});
						}
					}
				}	
        	});
		}catch (Exception e) {
			logger.error("",e);
		} 
	}
}
