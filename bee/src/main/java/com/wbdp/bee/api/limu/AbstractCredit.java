package com.wbdp.bee.api.limu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wbdp.bee.api.limu.common.HttpClient;
import com.wbdp.bee.api.limu.common.JsonUtils;
import com.wbdp.bee.api.limu.common.MDUtil;
import com.wbdp.bee.api.limu.common.StringUtils;

import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * @Description:立木征信主流程
 * @author: york
 * @date: 2017-02-27 14:38
 * @version: v1.2.0
 */
public class AbstractCredit{
	
	private static Logger logger=Logger.getLogger(AbstractCredit.class);

    public static HttpClient httpClient = new HttpClient();

    //定义共同参数
    public static final String apiUrl = "https://t.limuzhengxin.cn:3443/api/gateway";//服务地址---需客户指定---需联系立木开启IP白名单
    public static final String apiKey = "5579564587081017";//API授权 key ---需客户指定
    public static final String apiSecret = "3RSBFqoG5coo7J2TbUq5UBjjfxpgRbka";//API授权 secret---需客户指定

    public final String version = "1.2.0";//API版本号
    public final long timeInterval = 5000;//轮训时间 默认5秒

    public static String token = null;
    public String result = null;

    /**
     * 共同处理流程
     * @param reqParam
     * @throws Exception
     */
    public String doProcess(List<BasicNameValuePair> reqParam) throws Exception{

        //提交受理请求
        String json = httpClient.doPost(apiUrl, reqParam);
        System.out.println("json=" + json);
        if(StringUtils.isBlank(json)) {
        	logger.error(">>>>>>>>>>提交受理请求的字符串为空白串<<<<<<<<<<");
        } else {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readValue(json, JsonNode.class);
            String code = rootNode.get("code").textValue();

            if("0010".equals(code)) {//受理成功

                token = rootNode.get("token").textValue();
                return timer();//每5秒查询一次数据请求
            } else {
            	logger.error(">>>>>>>>>>提交受理失败<<<<<<<<<<");
            }
        }
       return null;
    }


    /**
     * @param @return
     * @throws Exception 
     * @throws
     * @Description:定时器
     */
    public String timer() throws Exception {
    	logger.warn(">>>>>>>>>>提交受理成功,轮询获取数据开始<<<<<<<<<<");
        //final Timer timer = new Timer();
        
        
        for(int i=0;i<5;i++){
        	TimeUnit.SECONDS.sleep(3);
        	String result=roundRobin();
        	if (result!=null) {
         	   logger.warn(">>>>>>>>>>提交受理成功,轮询获取数据结束<<<<<<<<<<");
         	     return result;
        	 }
        }
		return null;
        
        
//        timer.schedule(new TimerTask() {	
//            public void run() {
//                try {
//                    //循环取的状态，查询结果
//                    //停止循环(发送短信失败或信息查询成功)
//                    if (roundRobin()!=null) {
//                    	logger.warn(">>>>>>>>>>提交受理成功,轮询获取数据结束<<<<<<<<<<");
//                        timer.cancel();// 停止定时器
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    //异常
//                }
//            }
//        }, 0, timeInterval);
    }

    /**
     * @param @param  token
     * @param @return
     * @throws Exception true 停止循环(发送输入信息失败或信息查询成功)，false:未取到结果集或成功取结果集
     * @throws
     * @Description:循环取的状态，查询结果
     */
    public String roundRobin() throws Exception {

        //状态查询
        String json = httpClient.doPost(apiUrl, getReqParam());
        JsonNode rootNode = JsonUtils.toJsonNode(json);
        String token = JsonUtils.getJsonValue(rootNode, "token");
        String code =JsonUtils.getJsonValue(rootNode, "code");
        String msg = JsonUtils.getJsonValue(rootNode, "msg");
        logger.warn(">>>>>>>>>>循环取的状态:" + code+"<<<<<<<<<<");
        logger.warn(">>>>>>>>>>循环取的信息:" + json+"<<<<<<<<<<");
        
        if (StringUtils.isBlank(code)) {
            //未取到结果集
            return null;
        }
        
        if(JSONObject.parseObject(json).get("msg")!=""){
        	return json;
        }

        //0开头标处理成功相关
        if (code.startsWith("0")) {

            if ("0100".startsWith(code)) {//登陆成功
            	 logger.warn(">>>>>>>>>>登录结果查询 :" + msg+"<<<<<<<<<<");
                return null;
            } else if ("0006".equals(code)) {//等待输入信息

                JsonNode inputNode = rootNode.get("input");

                //获取等待输入类型
                String type = JsonUtils.getJsonValue(inputNode, "type");
                //图片验证码和二维码为base64编码的图片
                String value = JsonUtils.getJsonValue(inputNode, "value");
                //保存到本地作识别用-根据实际业务场景处理
                if (StringUtils.isNotBlank(value)) {
                    StringUtils.GenerateImage(value, token + ".jpeg");
                }

                //是否需要提交信息
                boolean bInput = false;
                switch (type) {
                    case "sms"://短信验证码
                        System.out.println("请提交收到的短信验证码 >>>>>");
                        bInput = true;
                        break;
                    case "img"://图片验证码
                        System.out.println("请提交识别出的图片验证码 >>>>>");
                        bInput = true;
                        break;
                    case "qr"://二维码
                        System.out.println("请扫描收到的图片二维码 >>>>>");
                        System.out.println("二维码保存在当前程序跟目录下,二维码名称为：【"+token + ".jpeg"+"】 >>>>>");
                        bInput = false;
                        break;
                    case "idp"://独立密码
                        System.out.println("请提交您的独立密码 >>>>>");
                        bInput = true;
                        break;
                    default:
                        break;
                }
                if (bInput) {
                    //等待输入信息
                    code = sendInput();
                    System.out.println("发送输入信息后查询状态：" + code);
                    if ("0009".equals(code)) {//结果    >>>>> 成功或失败
                        //继续轮训
                        return null;
                    } else {
                        //发送失败
                        return "{'code':'','msg':'发送失败'}";
                    }
                } else {
                    //继续轮训
                    return null;
                }
            } else if ("0000".startsWith(code)) {//成功
            	logger.warn(">>>>>>>>>>社保结果查询<<<<<<<<<<");
                //发送对象结果查询
                //getData();
                return getData();
            }
            //其他情况继续轮训
            else {
                return null;
            }
        }

        //其他异常停止循环
        return "{'code':'','msg':'其他异常停止循环'}";
    }

    /**
     * 签名转化
     *
     * @param reqParam
     * @return
     */
    public String getSign(List<BasicNameValuePair> reqParam) {

        StringBuffer sbb = new StringBuffer();
        int index = 1;
        for (BasicNameValuePair nameValuePair : reqParam) {
            sbb.append(nameValuePair.getName() + "=" + nameValuePair.getValue());
            if (reqParam.size() != index) {
                sbb.append("&");
            }
            index++;
        }
        String sign = sbb.toString();

        Map<String, String> paramMap = new HashMap<String, String>();
        String ret = "";
        if (!StringUtils.isEmpty(sign)) {
            String[] arr = sign.split("&");
            for (int i = 0; i < arr.length; i++) {
                String tmp = arr[i];
                if (-1 != tmp.indexOf("=")) {
                    paramMap.put(tmp.substring(0, tmp.indexOf("=")), tmp.substring(tmp.indexOf("=") + 1, tmp.length()));
                }

            }
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(paramMap.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, String>>() {
            public int compare(Map.Entry<String, String> o1, Map.Entry<String, String> o2) {
                int ret = 0;
                ret = o1.getKey().compareTo(o2.getKey());
                if (ret > 0) {
                    ret = 1;
                } else {
                    ret = -1;
                }
                return ret;
            }
        });

        StringBuilder sbTmp = new StringBuilder();
        for (Map.Entry<String, String> mapping : list) {
            if (!"sign".equals(mapping.getKey())) {
                sbTmp.append(mapping.getKey()).append("=").append(mapping.getValue()).append("&");
            }
        }
        sbTmp.setLength(sbTmp.lastIndexOf("&"));
        sbTmp.append(apiSecret);
        ret = MDUtil.SHA1(sbTmp.toString());

        //System.out.println(sb.toString());
        return ret;
    }

    /**
     * 获取共同提交参数
     *
     * @return
     */
    public List<BasicNameValuePair> getReqParam() {
        List<BasicNameValuePair> reqParam = new ArrayList<>();
        reqParam.add(new BasicNameValuePair("method", "api.common.getStatus"));//接口名称
        reqParam.add(new BasicNameValuePair("apiKey", apiKey));//API授权
        reqParam.add(new BasicNameValuePair("version", version));
        reqParam.add(new BasicNameValuePair("token", token));
        reqParam.add(new BasicNameValuePair("bizType", getBizType()));
        reqParam.add(new BasicNameValuePair("sign", getSign(reqParam)));//请求参数签名
        return reqParam;
    }


    /**
     * @param @return
     * @throws Exception
     * @throws
     * @Description:信息输入
     */
    public String sendInput() throws Exception {
        Scanner s = new Scanner(System.in);
        System.out.println("请输入上述提示信息（输入完按回车）：");
        List<BasicNameValuePair> reqParam = new ArrayList<>();
        reqParam.add(new BasicNameValuePair("method", "api.common.input"));//接口名称
        reqParam.add(new BasicNameValuePair("apiKey", apiKey));//API授权
        reqParam.add(new BasicNameValuePair("version", version));//调用的接口版本

        reqParam.add(new BasicNameValuePair("token", token));//请求标识
        reqParam.add(new BasicNameValuePair("input", s.nextLine()));//短信验证码/图片验证码/独立密码

        reqParam.add(new BasicNameValuePair("sign", getSign(reqParam)));//请求参数签名
        String json = httpClient.doPost(apiUrl, reqParam);
        return JsonUtils.getJsonValue(json, "code");
    }


    /**
     * @param
     * @throws
     * @Description:查询的结果集(结果查询)
     */
    public String getData() {
        List<BasicNameValuePair> reqParam = new ArrayList<BasicNameValuePair>();
        reqParam.add(new BasicNameValuePair("method", "api.common.getResult"));//接口名称	是	api.common.getResult
        reqParam.add(new BasicNameValuePair("apiKey", apiKey));//API授权
        reqParam.add(new BasicNameValuePair("version", version));//调用的接口版本，可选值：1.0.0


        reqParam.add(new BasicNameValuePair("token", token));//请求标识
        reqParam.add(new BasicNameValuePair("bizType", getBizType()));//手机短信验证码

        reqParam.add(new BasicNameValuePair("sign", getSign(reqParam)));//请求参数签名
        String json = httpClient.doPost(apiUrl, reqParam);
        logger.warn(">>>>>>>>>>社保结果集:"+json+"<<<<<<<<<<");
        return json;
    }

    /**
     * 获取业务类型
     *
     * @return
     */
    public String getBizType() {
        throw new RuntimeException("请重写该方法");
    }


}
