package com.sorl.backend;


import org.bson.Document;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.async.SingleResultCallback;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
* 
* @description 数据处理器，用于处理设备50发送到服务器的ByteBuf数据
* @remarks 数据格式：[0:PACKAGE_TIME_IO_LENGTH-1]：[时间:ADC数据长度:IO:ID:校验码]；
* [PACKAGE_TIME_IO_LENGTH-1:PACKAGE_TIME_IO_LENGTH+MAX_TEST_NAME-1]：[时间:ADC数据长度:IO:ID:校验码]；
* @examp 00 00 10 10 48 10 01 00 10 00 00 00 01 01 00 48 74 65 73 74 31 5f 32 30 31 39 30 33 30 31 5f 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 02 00 05 01 03 00 06 01 02 00 05 01 03 00 05
* @remoteIP 115.159.154.160
* @author  nesc420
* @data    2019-4-8
* @version 0.0.3
*/
public class DataProcessor {
	//一组ADC数据有多少个Byte，共4个通道，每个通道2个bytes
	private final int LENGTH_ONE_GROUP_ADC = 8;
	//一组CAN数据有多少个Byte,1byte用于存储CAN1或者CAN2，4bytes存储偏移时间（相对于帧头内保存的时间）,19bytes存储数据
	private final int LENGTH_ONE_GROUP_CAN = 24;
	//模组id最大不超过
	private final int  WIFI_CLIENT_ID_MAX= 255;
	//adc数据通道最多不超过
	private final byte  ADC_CHANNEL_MAX= 4;
	//ADC一个周期所占的bytes
	private final short ADC_BYTES_NUM = 2*ADC_CHANNEL_MAX;
	//测试名称长度
	private final static byte MAX_TEST_NAME = 64;
	//时间、IO、id、数据类型这些数据的长度
	private final static byte PACKAGE_TIME_IO_LENGTH = 16;
	//一帧的头的长度，包括测试名称、yyyy_mm_dd、headtime、data_count等
	private final static byte HEAD_FRAME_LENGTH = MAX_TEST_NAME + PACKAGE_TIME_IO_LENGTH;
	//校验开始的位置，和headtime的低八位相等
	private final short CHECK_UBYTE = 15;
	//年月日开始的下标,下标越大，越高位
	private final int YYYY_MM_DD_START_IDX = 0;
	//毫秒开始的下标
	private final int HEADTIME_START_IDX = 4;
	//数据量大小开始的下标
	private final int DATA_COUNT_START_IDX = 8;
	//这里保存wifi模组的id的下标
	private final int WIFI_CLIENT_ID_IDX = 12;
	//IO开始的地址，1byte最多可保存8个IO数据
	private final int IO_IN_IDX = 13;
	
	 //数据类型，包括CAN数据和ADC数据
	private final int DATA_TYPE_IDX = 14;
	//CAN数据标记
	private final static short CAN_DATA_PACKAGE_LABEL = 1;
	//ADC数据标记
	private final static short ADC_DATA_PACKAGE_LABEL = 2;
	//CAN数据String标记
	private final static String CAN_DATA_PACKAGE_STR = "CAN";
	//ADC数据String标记
	private final static String ADC_DATA_PACKAGE_STR = "ADC";
	//测试名称紧接着time io等
	private final int TEST_NAME_IDX = PACKAGE_TIME_IO_LENGTH;
	
	private int BytebufLength;
	private short checkUbyte;
	private short nodeId;
	private long yyyy_mm_dd;
	private long headtime;
	private long data_count;
	private short io1,io2;
	//数据类型 @ref “CAN“ or “ADC“
	private String dataType;
	private String testName;
	
	//MongoDB的数据key：本次测试的名称
	public final static String MONGODB_KEY_TESTNAME = "test";
	//MongoDB的数据key：本个设备的名称
	public final static String MONGODB_KEY_NODE_ID = "nodeId";
	//MongoDB的数据key：年月日
	public final static String MONGODB_KEY_YYYYMMDD = "yyyy_mm_dd";
	//MongoDB的数据key：每天的时间精确到1ms
	public final static String MONGODB_KEY_HEADTIME = "headtime";
	//MongoDB的数据key：有多少个byte数据 
	public final static String MONGODB_KEY_DATA_COUNT = "data_count";
	//MongoDB的数据key：数字通道1
	public final static String MONGODB_KEY_IO1 = "io1";
	//MongoDB的数据key：数字通道2
	public final static String MONGODB_KEY_IO2 = "io2";
	//MongoDB的数据key：数据类型，包括ADC和CAN
	public final static String MONGODB_KEY_DATA_TYPE = "dataType";
	//MongoDB的数据key：adc的数值
	public final static String MONGODB_KEY_ADC_VAL = "adc_val";
	//MongoDB的数据key：原始数据
	public final static String MONGODB_KEY_RAW_DATA = "raw_data";
	private MyMongoDB mongodb;
	/**
	* 数据处理obj的构造函数
	*
	* @param dbname 数据库名称
	* @return 无
	* @throws 无
	*/		
	public void setMongodb(MyMongoDB myMongoDB) {
		mongodb = myMongoDB;//这个mongodb是依赖注入的
	}
	
	SingleResultCallback<Void> callback;
	/**
	* 总的数据包的解析和存储方法
	*
	* @param msg 传入的ByteBuf数据。
	* @return none
	* @throws none
	*/	
	public void dataProcess(ByteBuf msg){
		//更新帧头的信息
		if(!getFrameHead(msg)) {
			return;
		}
		byte[] byteRawData = new byte[msg.readableBytes()];
		msg.readBytes(byteRawData);//读取msg，写入到byteRawData
		Document doc = new Document(DataProcessor.MONGODB_KEY_NODE_ID,nodeId)//该包的节点
				.append(DataProcessor.MONGODB_KEY_YYYYMMDD, yyyy_mm_dd)//改包的年月日
				.append(DataProcessor.MONGODB_KEY_HEADTIME,headtime)//改包的起始时间
				.append(DataProcessor.MONGODB_KEY_IO1,io1)//数字通道1
				.append(DataProcessor.MONGODB_KEY_IO2,io2)//数字通道2
				.append(DataProcessor.MONGODB_KEY_DATA_COUNT,data_count)//数据个数
				.append(DataProcessor.MONGODB_KEY_DATA_TYPE, dataType)//数据类型
				.append(DataProcessor.MONGODB_KEY_TESTNAME,testName)//测试名称
				.append(DataProcessor.MONGODB_KEY_RAW_DATA,byteRawData );//原始数据
		//解析数据
		if(dataType.equals(ADC_DATA_PACKAGE_STR)) {
			//生成document
			BasicDBObject bdoAdcVal = getAdcVal4CH(msg,(short)(data_count));
			//解析后的ADC数字量
			doc.append(DataProcessor.MONGODB_KEY_ADC_VAL,bdoAdcVal);
		}else if(dataType.equals(CAN_DATA_PACKAGE_STR)) {
			//TODO:(songchaochao,19-4-9,如果是CAN数据则不解析)
		}
		
		/*doc存入数据库*/
		//mongodb.insertOne已加锁
		try{
			mongodb.insertOne(doc, new SingleResultCallback<Void>() {
			    public void onResult(final Void result, final Throwable t) {
			    	///用于指示DOC是否成功插入
//			    		System.out.println("Document inserted!");
			    }});			
		}catch(Exception e) {
			System.err.println(e);
		}		
	}

	/**
	* 数据包的校验方法
	*
	* @param checkByte1,checkByte2： 两个校验字节，相等才能通过
	* @return true：通过;false:不通过
	* @throws 无
	*/
	private boolean isRightPkg(short checkByte1,short checkByte2){
		if(checkByte1 == checkByte2) {
			return true;
		}
		else {
			return false;
		}
	}
	/**
	* 数据包的提取前16bits帧头数据和测试名称
	* 
	* YYYY_MM_DD:年月日32bits[0:3], HeadTime:毫秒32bits[4:7], count:数据长度32bits[8:11], 
	* 
	* nodeId:模组id8bits[12],IO:数字电平[13],dataType:数据类型[14],checkUbyte:校验8bits[15]
	*
	* @param msg 
	* @note msg的两个校验字节msg[4]和msg[15]，相等才能通过
	* @return true：成功获取数据帧头;false:数据有问题
	* @throws 无
	*/
	private boolean getFrameHead(ByteBuf msg) {
		//得到帧头+实际数据的Bytebuf字节长度
		BytebufLength = msg.readableBytes();
		//获取设备的id
		nodeId = msg.getUnsignedByte(WIFI_CLIENT_ID_IDX);
//		System.out.println("Node Id:"+nodeId);
		//校验设备的id
		if((nodeId<0) ||(nodeId>WIFI_CLIENT_ID_MAX)) {
			System.out.println("NodeId Error : Pkg Abandoned!");
			return false;
		}
		//获取headtime/微秒
		headtime = (long)(msg.getUnsignedByte(HEADTIME_START_IDX)|
				(msg.getUnsignedByte(HEADTIME_START_IDX+1)<<8)|
				(msg.getUnsignedByte(HEADTIME_START_IDX+2)<<16)|
				(msg.getUnsignedByte(HEADTIME_START_IDX+3)<<24));
		//获取校验byte
		checkUbyte = msg.getUnsignedByte(CHECK_UBYTE);
		//校验位校验，headtime的最低8bits需要和帧头校验位相同
		if(!isRightPkg((short)(headtime&0xff),(short)checkUbyte)){
			System.out.println("CheckUbyte Error : Pkg Abandoned");
			return false;
		}
		yyyy_mm_dd = (long)(msg.getUnsignedByte(YYYY_MM_DD_START_IDX)|
				(msg.getUnsignedByte(YYYY_MM_DD_START_IDX+1)<<8)|
				(msg.getUnsignedByte(YYYY_MM_DD_START_IDX+2)<<16)|
				(msg.getUnsignedByte(YYYY_MM_DD_START_IDX+3)<<24));
		data_count = (long)(msg.getUnsignedByte(DATA_COUNT_START_IDX)|
				(msg.getUnsignedByte(DATA_COUNT_START_IDX+1)<<8)|
				(msg.getUnsignedByte(DATA_COUNT_START_IDX+2)<<16)|
				(msg.getUnsignedByte(DATA_COUNT_START_IDX+3)<<24));
		//数据个数的校验
		if((data_count<0)||(data_count !=(BytebufLength - HEAD_FRAME_LENGTH))) {
			System.out.println("Count Error : Abandoned");
			return false;
		}
		//获取io电平
		io1 = (short) (msg.getUnsignedByte(IO_IN_IDX) &  ((short)0x0001));
		io2 = (short) ((msg.getUnsignedByte(IO_IN_IDX) &  ((short)0x0002))>>1);
		//获取数据类型，并采取不同的处理措施
		if(CAN_DATA_PACKAGE_LABEL == (short) msg.getUnsignedByte(DATA_TYPE_IDX)) {
			dataType = CAN_DATA_PACKAGE_STR;
			
			if(BytebufLength<=HEAD_FRAME_LENGTH+LENGTH_ONE_GROUP_CAN)
			{
				System.out.println("Error : length = "+msg.readableBytes()+
						", <= SMALLEST LIMIT("+(HEAD_FRAME_LENGTH+LENGTH_ONE_GROUP_CAN)+")");
				return false;
			}
		}else if(ADC_DATA_PACKAGE_LABEL == (short) msg.getUnsignedByte(DATA_TYPE_IDX)) {
			dataType = ADC_DATA_PACKAGE_STR;
			
			if(BytebufLength<=HEAD_FRAME_LENGTH+LENGTH_ONE_GROUP_ADC)
			{
				System.out.println("Error : length = "+msg.readableBytes()+
						", <= SMALLEST LIMIT("+(HEAD_FRAME_LENGTH+LENGTH_ONE_GROUP_ADC)+")");
				return false;
			}
		}else {
			System.out.println("Data Type Error : Abandoned");
			return false;
		}
		//获取测试名称
		ByteBuf testNameTemp = Unpooled.buffer(DataProcessor.MAX_TEST_NAME);
		msg.getBytes(TEST_NAME_IDX,testNameTemp,DataProcessor.MAX_TEST_NAME);
		this.testName = testNameTemp.toString(CharsetUtil.UTF_8);
		testName = testName.trim();//将最后的空字符去掉

		return true;
	}

	/**
	* 数据包的提取,除了前16bits帧头外的adc数据，adc数据以[channel1低八位,channel1高八位,channel2低八位,channel2高八位...]传输
	* 
	* channle_num是通道数，adc_count_short是每个通道包含多少个数据（数据12bit，用hsort）
	* ----!!必须在getFrameHead更新后才能调用!!------
	*
	* @param msg 包括帧头在内的所有数据
	* @param adc_count adc数据的byte位数
	* @return BasicDBObject
	* @throws 无
	*/
	private BasicDBObject getAdcVal4CH(ByteBuf msg,int adc_count) {
		int adc_count_short = adc_count/2/ADC_CHANNEL_MAX;
		BasicDBList ch1 = new BasicDBList();
		BasicDBList ch2 = new BasicDBList();
		BasicDBList ch3 = new BasicDBList();
		BasicDBList ch4 = new BasicDBList();
		for(int idx = 0,idx_start; idx<adc_count_short ; idx++) {
			idx_start = idx * 8 + HEAD_FRAME_LENGTH;
			ch1.add((short)( (msg.getUnsignedByte(idx_start)<<8) | 
							(msg.getUnsignedByte(idx_start + 1)) ));
			ch2.add((short)( (msg.getUnsignedByte(idx_start + 2)<<8) |   
							(msg.getUnsignedByte(idx_start + 3)) ));
			ch3.add((short)( (msg.getUnsignedByte(idx_start + 4)<<8) | 
							(msg.getUnsignedByte(idx_start + 5)) ));
			ch4.add((short)( (msg.getUnsignedByte(idx_start + 6)<<8) | 
							(msg.getUnsignedByte(idx_start + 7)) ));
		}
		return (new BasicDBObject()).append("ch1", ch1).append("ch2", ch2).append("ch3", ch3).append("ch4", ch4);
	}
	/**
	* 打印每个通道的两个电压数值，用来检查。以电压形式展示。
	* 
	* 
	* @param buf 以short格式（每个short对因一个12位adc数据）存储的adc数据
	* @param idx1 第一个数据下标
	* @param idx2 第二个数据下标
	* @return 无
	* @throws 无
	*/	
	private void printAdcBuf_4Ch(short[][] buf,short idx1,short idx2){
		try {
			System.out.printf("ch1: %.5f V   %.5f V\n", 5.0*buf[(byte)0][(short)idx1]/4096.0,5.0*buf[(byte)0][(short)idx2]/4096.0);
			System.out.printf("ch2: %.5f V   %.5f V\n", 5.0*buf[(byte)1][(short)idx1]/4096.0,5.0*buf[(byte)1][(short)idx2]/4096.0);
			System.out.printf("ch3: %.5f V   %.5f V\n", 5.0*buf[(byte)2][(short)idx1]/4096.0,5.0*buf[(byte)2][(short)idx2]/4096.0);
			System.out.printf("ch4: %.5f V   %.5f V\n", 5.0*buf[(byte)3][(short)idx1]/4096.0,5.0*buf[(byte)3][(short)idx2]/4096.0);				
		}catch(ArrayIndexOutOfBoundsException a) {  //数组下标越界
			System.out.println("Exception: ArrayIndexOutOfBoundsException at printAdcBuf_4Ch()");
			return;
		}
	
	}
	/**
	* 数据包的提取,除了前16bits帧头外的adc数据，adc数据以[channel1低八位,channel1高八位,channel2低八位,channel2高八位...]传输
	* 
	* channle_num是通道数，adc_count_short是每个通道包含多少个数据（数据12bit，用hsort）
	* ----!!必须在getFrameHead更新后才能调用!!------
	*
	* @param ByteBuf 包括帧头的数据
	* @param adc_count adc数据的字节数
	* @return short[][]
	* @throws 无
	*/
	private short[][] getAdcVal(ByteBuf msg,int adc_count) {
		int adc_count_short = adc_count/2/ADC_CHANNEL_MAX;
		short[][] buf = new short[ADC_CHANNEL_MAX][adc_count_short];
		for(int idx = 0; idx<adc_count ; idx += 2) {
			buf[(byte)(idx % ADC_BYTES_NUM)/2][(short)(idx / ADC_BYTES_NUM)] = 
					(short)(  (msg.getUnsignedByte(idx + HEAD_FRAME_LENGTH)<<4) | 
							(msg.getUnsignedByte(idx+HEAD_FRAME_LENGTH+1)>>4) );//>>有符号右移，>>>也可以
			
		}
		return buf;	
	}
}
