/**
 * 
 */
package test20171212;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/** 
 * @ClassName:     Test_20171212 
 * @Description:   TODO
 * @author:        HongLC 
 * @date:          2017年12月12日 下午2:25:45 
 *  
 */
public class Test_20171212 {
	
	//private static final Logger log=Logger.getLogger(Test_20171212.class);
	
	public static void main(String[] args) {
		
		
		String content = "asd <img src=\"http://www.baidu.com/img/baidu_sylogo1.gif\" />sd";
		List<String> list=pareImgs(content);
		for (String src : list) {
			//获取图片名
			System.out.println(src);
			String name=src.substring(src.lastIndexOf("/")+1);
			String path="F:/hlc/operationFile/downFile";
			//downNetPics(src,name,path);
		}
	}
	public static List<String> pareImgs(String content){
		List<String> imgs=new ArrayList<String>();
		String img = "";
		Pattern p_image;
		Matcher m_image;
		// String regEx_img = "<img.*src=(.*?)[^>]*?>"; //图片链接地址
		String regEx_img = "&lt;img.*src\\s*=\\s*(.*?)[^>]*?&gt;";
		p_image = Pattern.compile(regEx_img, Pattern.CASE_INSENSITIVE);
		m_image = p_image.matcher(content);
		while (m_image.find()) {
			// 得到<img />数据
			img = m_image.group();
			// 匹配<img>中的src数据
			Matcher m = Pattern.compile("src\\s*=\\s*\"?(.*?)(\"|>|\\s+)")
					.matcher(img);
			while (m.find()) {
				imgs.add(m.group(1));
			}
		}
		
		return imgs;
	}
	
	public static void downNetPics(String urlstring,String filename,String path){
		try {
			URL url = new URL(urlstring); 
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();  
			conn.setConnectTimeout(5000);
			
			InputStream is = conn.getInputStream();
		
			byte[] b = new byte[1024];
			 
			int len;
			 
			File sf=new File(path);
			if(!sf.exists()){
				sf.mkdirs();
			}
			OutputStream os = new FileOutputStream(sf.getPath()+"/"+filename);
			 
			while ((len = is.read(b)) != -1) {
				os.write(b, 0, len);		
			}			 
			os.close();			  
			is.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
}
