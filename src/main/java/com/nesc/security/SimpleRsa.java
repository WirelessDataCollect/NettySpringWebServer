package com.nesc.security;

import java.util.*;
import java.io.InputStream;
import java.math.*;


/**
* 
* 简单的RSA加密算法
*
* @author  nesc418
* @Date    2018-10-28
* @version 0.0.1
*/
public class SimpleRsa {
	private BigInteger bigPrime_p,bigPrime_q;
	private BigInteger bigPhi;
	private BigInteger publicN; 
	private int publicE,privateKey;//不能太大
	/**
	 * 初始化密钥：(1)获取素数p和Q(2)计算n=pq,phi=(p-1)(q-1)(3)选择e,使得e远小于phi，且gcd(e,phi)=1
	 * (4)求d，使得ed=1 (mod phi),d为私钥(d为e的逆元，满足e*d=phi * K + 1)
	 * (5)发布(n,e)，即为公钥(n,e)
	 * @param bitLength 不要超过11，不然很可能会溢出造成计算失败
	 */
	public void setKey(int bitLength) {
		//(1)获取素数p和Q
		Random rnd_p = new Random(new Date().getTime());
		Random rnd_q = new Random(new Date().getTime()+new Date().getTime()%10);
		Random rnd_e = new Random(new Date().getTime()+new Date().getTime()%21);
		this.bigPrime_p = BigInteger.probablePrime(bitLength, rnd_p);
		this.bigPrime_q = BigInteger.probablePrime(bitLength, rnd_q);
		
		//(2)计算n=pq,phi=(p-1)(q-1)
		this.publicN = bigPrime_p.multiply(bigPrime_q);
		this.bigPhi = bigPrime_p.subtract(BigInteger.ONE).multiply(bigPrime_q.subtract(BigInteger.ONE));
		
		//(3)选择e,使得e远小于phi，且gcd(e,phi)=1
//		for(int e=MAX_PUBLIC_E.intValue();;e--) {
//			if(this.bigPhi.gcd(BigInteger.valueOf(e)).equals(BigInteger.ONE)) {
//				this.publicE = e;
////				if(this.publicE>Integer.MAX_VALUE) {
////					System.err.println("public e is TOO BIG");
////				}
//				break;
//			}
//		}
		this.publicE = BigInteger.probablePrime(15, rnd_e).intValue();
		
		//(4)求d，使得ed=1 (mod phi),d为私钥(d为e的逆元，满足e*d=phi * K + 1)
		for(BigInteger k=BigInteger.ONE;;k=k.add(BigInteger.ONE)) {
			if(this.bigPhi.multiply(k).add(BigInteger.ONE).mod(BigInteger.valueOf(this.publicE))
					.equals(BigInteger.ZERO)) {
				this.privateKey = (this.bigPhi.multiply(k).add(BigInteger.ONE).
						divide(BigInteger.valueOf(this.publicE))).intValue();
				System.out.println("K="+String.valueOf(k));
				break;
			}
		}
		System.out.printf("P = %d\nQ = %d\n",this.bigPrime_p,this.bigPrime_q);
		System.out.printf("Phi = %d\n",this.bigPhi);
		System.out.printf("n = %d\n",this.publicN);
		System.out.printf("e = %d\n",this.publicE);
		System.out.printf("d = %d\n",this.privateKey);
	}
	
	public BigInteger getEncryptedVal(BigInteger val) {
		return val.pow(this.publicE).mod(this.publicN);
	}
	public BigInteger getDencryptedVal(BigInteger val) {
		return val.pow(this.privateKey).mod(this.publicN);
	}	
	
//	public static void main(String[] args) {
//		SimpleRsa rsa = new SimpleRsa();
//		rsa.setKey(Integer.parseInt(args[0]));
//		BigInteger EncryptedVal = rsa.getEncryptedVal(BigInteger.valueOf(Integer.parseInt(args[1])));
//		
//		System.out.printf("getEncryptedVal : %d\n",EncryptedVal.intValue());
//		System.out.printf("getDencryptedVal : %d\n",rsa.getDencryptedVal(EncryptedVal).intValue());
//	}
}

