package com.sorl.backend;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.log4j.Logger;
import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.ClientSession;
import com.mongodb.async.client.FindIterable;
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
	 * 基于从testName提取出来的isodate为基础，进行数据清除
	 * 
	 * @return none
	 */
	@Scheduled(cron="0 0 4 * * ?")  //凌晨4点执行数据库清空指令（DAYS_BEFORE_TODAY天之前的数据）
	public void mgdClearByIsodate() {
		try {
			logger.info("mgdClearByIsodate Start Clearing N-day-before datas and configurations");
			MyMongoDB generalMgdInterface = (MyMongoDB)App.getApplicationContext().getBean("generalMgdInterface");
			MyMongoDB testInfoMongdb = (MyMongoDB)App.getApplicationContext().getBean("testConfMongoDB");
			MyMongoDB dataMgd = (MyMongoDB)App.getApplicationContext().getBean("myMongoDB");
		
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
						logger.info(String.format("Date today ： " + sdf.format(calendar.getTime()) + TaskJob.hms4MgdClearByIsodate));
						//N天前的日期
						calendar.add(Calendar.DATE, TaskJob.DAYS_BEFORE_TODAY); 
						String upperBound = sdf.format(calendar.getTime()) + TaskJob.hms4MgdClearByIsodate;
						logger.info(String.format("Date before N days ： " + upperBound));
						BasicDBObject filter = new BasicDBObject();
						filter.put(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_ISODATE, new BasicDBObject("$lte",upperBound));
						// 返回的document包含那些内容，后面只有testname需要
						BasicDBObject projections = new BasicDBObject();
						projections.append(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME, 1).append("_id", 0);
						//设置指向配置文件的col
						generalMgdInterface.resetCol(testInfoMongdb.getColName());
						FindIterable<Document> findIter = generalMgdInterface.collection.find(filter).projection(projections);
						//设置指向数据的col
						generalMgdInterface.resetCol(dataMgd.getColName());
						findIter.forEach(new Block<Document>() {
							@Override
							public void apply(Document doc) {
								try {
									logger.info(doc.toJson());
									logger.info(String.format("for each db.col(%s.%s) ", generalMgdInterface.getDbName(),generalMgdInterface.getColName()));
									String testName = (String)doc.get(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_TESTNAME);
									//删除ADC和CAN数据
									generalMgdInterface.collection.deleteMany(new BasicDBObject(DataProcessor.MONGODB_KEY_TESTNAME,testName), new SingleResultCallback<DeleteResult>() {
										@Override
										public void onResult(final DeleteResult result, final Throwable t) {
											logger.info(String.format("Cleared %d documents  of db.col(%s.%s)", result.getDeletedCount(),
													dataMgd.getDbName(),dataMgd.getColName()));
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
										logger.info(String.format("Cleared %d configurations of db.col(%s.%s)", result.getDeletedCount(),
												testInfoMongdb.getDbName(),testInfoMongdb.getColName()));
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
	//配置任务的执行时间，可以配置多个
	private final static String hms4MgdClearByInsertIsodate = "T03:00:00";
	//每个月都要清除
	@Scheduled(cron="0 0 3 1 * ?")  //每个月1号凌晨3点清除一次
	public void mgdClearByInsertIsodate() {
		try {
			logger.info("mgdClearByInsertIsodate Start Clearing N-day-before datas and configurations");
			MyMongoDB generalMgdInterface = (MyMongoDB)App.getApplicationContext().getBean("generalMgdInterface");
			MyMongoDB testInfoMongdb = (MyMongoDB)App.getApplicationContext().getBean("testConfMongoDB");
			MyMongoDB dataMgd = (MyMongoDB)App.getApplicationContext().getBean("myMongoDB");
		
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
						//testInfoMgd的dataMgd的插入文档时间字段相同，均为insertIsodate
						filter.put(TCP_ServerHandler4PC.TESTINFOMONGODB_KEY_INSERT_ISO_DATE, new BasicDBObject("$lte",upperBound));
						//设置指向配置文件的col
						generalMgdInterface.resetCol(testInfoMongdb.getColName());
						generalMgdInterface.collection.deleteMany(filter, new SingleResultCallback<DeleteResult>() {
							@Override
							public void onResult(final DeleteResult result, final Throwable t) {
								logger.info(String.format("Cleared %d configurations of db.col(%s.%s)", result.getDeletedCount(),
										testInfoMongdb.getDbName(),testInfoMongdb.getColName()));
								filter.clear();
								filter.put(DataProcessor.MONGODB_KEY_INSERT_ISO_DATE, new BasicDBObject("$lte",upperBound));
								//设置指向数据的col
								generalMgdInterface.resetCol(dataMgd.getColName());
								generalMgdInterface.collection.deleteMany(filter, new SingleResultCallback<DeleteResult>() {
									@Override
									public void onResult(final DeleteResult result, final Throwable t) {
										logger.info(String.format("Cleared %d documents of db.col(%s.%s)", result.getDeletedCount(), 
												dataMgd.getDbName(),dataMgd.getColName()));
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
}
