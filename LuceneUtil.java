package net.jweb.common.lucene;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.jweb.common.model.Annex;
import net.jweb.common.model.BizMailArchive;
import net.jweb.common.model.BizMailAuthority;
import net.jweb.common.model.BizMailContent;
import net.jweb.common.model.BizMailModule;
import net.jweb.common.model.CommonFileCache;
import net.jweb.common.model.Department;
import net.jweb.common.model.FileCache;
import net.jweb.common.model.Group;
import net.jweb.common.model.TikaFileContent;
import net.jweb.common.model.ShareFileCache;
import net.jweb.common.model.User;
import net.jweb.common.services.BaseService;
import net.jweb.common.services.PageService;
import net.jweb.common.util.DiskUtil;
import net.jweb.common.util.Page;
import net.jweb.common.util.SpringUtil;
import net.jweb.common.util.StringUtil;
import net.jweb.hibernate.BaseServiceUtil;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.wltea.analyzer.lucene.IKAnalyzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@SuppressWarnings("unchecked")
public class LuceneUtil {	
	private static Logger logger = Logger.getRootLogger();
	private static IndexWriter myIwriter = null;



	private static IndexWriter shareIwriter = null;
	private static IndexWriter commIwriter = null;
	private static String myLucenePath;//myfile lucene 路径
	private static String shareLucenePath;//sharefile lucene 路径
	private static String commLucenePath;//commonfile lucene 路径
	private static String dicstorePath;//commonfile lucene 路径
	
	private static String bizmailPath;//关键词 路径
	private static BaseService baseService = null;
	static
	{
		try
		{			
			myLucenePath = DiskUtil.getLucenePath("my/");			//获取myfile lucene 路径
			shareLucenePath = DiskUtil.getLucenePath("share/");				//获取sharefile lucene 路径
			commLucenePath = DiskUtil.getLucenePath("comm/");			//获取commonfile lucene 路径
			dicstorePath = DiskUtil.getLucenePath("dicstore/");	//获取同义词库路径
//			baseService = (BaseService) BaseServiceUtil.getBean("baseService");
			
			bizmailPath = DiskUtil.getLucenePath("bizmail/");	//获取关键字路径
			baseService = SpringUtil.getBaseService();
			
		}
		catch(Exception e)
		{
			logger.error("Lucene异常：",e);
		}
	}
	
	/**
	 * 对lucene中的特殊字符进行转义处理
	 * @param keywords
	 * @return
	 */
	public static String filterKeywords(String keywords) {
		//Lucene支持转义特殊字符，因为特殊字符是查询语法用到的。现在，特殊字符包括 
		//+ – && || ! ( ) { } [ ] ^ ” ~ * ? : /
		
		//文件名不能包含的特殊字符    / \ < > * ? : " |
		//"+","–","&","!","(",")","{","}","[","]","~"
		if(StringUtils.isNotEmpty(keywords))
		{
			keywords = keywords.replace("-", "/-");
			keywords = keywords.replace("+", "/+");
			keywords = keywords.replace("~", "/~");
			keywords = keywords.replace("&", "/&");
			//--~`^(){}_- =,.!$-:;@
			keywords = keywords.replace("`", "/`");
			keywords = keywords.replace("^", "/^");
			keywords = keywords.replace("(", "/(");
			keywords = keywords.replace(")", "/)");
			keywords = keywords.replace("{", "/{");
			keywords = keywords.replace("}", "/}");
			keywords = keywords.replace("_", "/_");
			keywords = keywords.replace("=", "/=");
			keywords = keywords.replace(",", "/,");
			keywords = keywords.replace(".", "/.");
			keywords = keywords.replace("!", "/!");
			keywords = keywords.replace("$", Matcher.quoteReplacement("/$"));
			keywords = keywords.replace("@", "/@");
			keywords = keywords.replace(":", "/:");
			keywords = keywords.replace(";", "/;");
		}
		//System.out.println("keywords:"+keywords);
		return keywords;
	}
	
	/**
	 * 拆分关键字 用于实现:部分搜索关键词的拆分搜索  (如: DK-LSL+UL-S1 分成成 DK LSL UL S 1 , XG15-A427分解成XG 15 A 427等形式)  
	 * @param keywords
	 * @return
	 */
	public static String splitKeywords(String keywords)
	{
		if(null == keywords || ""== keywords)
		{
			return "";
		}
		String regEx = "[\\d]+";
		Pattern p = Pattern.compile(regEx);
		Matcher m = p.matcher(keywords);
		String newKeywords = keywords;
		while(m.find())
		{
			newKeywords = newKeywords.replaceFirst(m.group(0), " "+m.group(0)+" ");
		}
		regEx = "[`~!@#$%^&*()+=|\\-{}'：；'，\\[\\].<>/?~！@#￥%;*（）——+|{}【】｀；：”“’。，、？]+";
		p = Pattern.compile(regEx);
		m = p.matcher(newKeywords);
		if(m.find())
		{
			newKeywords = m.replaceAll(" ");
		}
		return newKeywords;
	}
	/**
	 * 拆分关键字 用于实现:部分搜索关键词的拆分搜索  (如: DK-LSL+UL-S1 分成成 DK LSL UL S 1 , XG15-A427分解成XG 15 A 427等形式) 
	 * 但不拆+号
	 * @param keywords
	 * @return
	 */
	public static String splitKeywords2(String keywords)
	{
		if(null == keywords || ""== keywords)
		{
			return "";
		}
		String regEx = "[\\d]+";
		Pattern p = Pattern.compile(regEx);
		Matcher m = p.matcher(keywords);
		String newKeywords = keywords;
		while(m.find())
		{
			newKeywords = newKeywords.replaceFirst(m.group(0), " "+m.group(0)+" ");
		}
		regEx = "[`~!@#$%^&*()=|\\-{}'：；'，\\[\\].<>/?~！@#￥%;*（）——|{}【】｀；：”“’。，、？]+";
		p = Pattern.compile(regEx);
		m = p.matcher(newKeywords);
		if(m.find())
		{
			newKeywords = m.replaceAll(" ");
		}
		return newKeywords;
	}
	
	/**
	 * 获取关键字模糊匹配表达式
	 * @param keywords
	 * @return
	 */
	public static String getBlurkey(String keywords) {
		String blurkey = keywords;
		if(keywords.indexOf("-")<0 && keywords.indexOf("+")<0 && !keywords.endsWith("*"))
			//&& keywords.indexOf("~")<0&& keywords.indexOf("`")<0&& keywords.indexOf("^")<0&& keywords.indexOf("(")<0
			//&& keywords.indexOf(")")<0&& keywords.indexOf("{")<0&& keywords.indexOf("}")<0&& keywords.indexOf("_")<0
			//&& keywords.indexOf("=")<0&& keywords.indexOf(",")<0&& keywords.indexOf(".")<0&& keywords.indexOf("!")<0
			//&& keywords.indexOf("$")<0&& keywords.indexOf("@")<0&& keywords.indexOf(":")<0&& keywords.indexOf(";")<0)
		{
			blurkey = keywords+"*";//模糊匹配
		}
		return blurkey;
	}
	
	public static Page searchMyFiles(String keywords,int userId,PageService pageService,int nowPage, int pageSize, Map<String,String> param)
	{
		Page page = new Page();
		if(StringUtils.isNotEmpty(keywords))
		{
			keywords = filterKeywords(keywords);
			String blurkey = getBlurkey(keywords);
//		
			IndexReader iReader = null;
			IndexSearcher iSearcher = null;
			IndexSearcher iSearcher1 = null;
			Analyzer analyzer = null;
			Analyzer ikAnalyzer = null;
			try
			{
				File file = new File(myLucenePath);				//获取lucene文件夹
				FSDirectory dir = FSDirectory.open(file);
				if(IndexReader.indexExists(dir))
				{
					iReader = IndexReader.open(dir,true);//只读模式打开
				}
				else
				{
					return page;
				}
				
				String searchScope = param.get("scope");
				iSearcher = new IndexSearcher(iReader);		//创建索引查询器
				analyzer = new IKAnalyzer();//使用IK分词器
				ikAnalyzer = new IKAnalyzer(true);//使用IK智能分词器
				BooleanQuery queryShould = new BooleanQuery(); 				//将条件进行组合
				
				String[] fields = null;
				if("name".equalsIgnoreCase(searchScope))//通过标题搜索
				{
					fields = new String[] { "fileName" };
				}
				else if("tag".equalsIgnoreCase(searchScope))//通过标签搜索
				{
					fields = new String[] { "tags" };
				}
				else if("description".equalsIgnoreCase(searchScope))//通过描述搜索
				{
					fields = new String[] { "description" };
				}
				else if("nametag".equalsIgnoreCase(searchScope))//通过标签、标题搜索
				{
					fields = new String[] { "fileName", "tags"};
				}
				else if("tagdescription".equalsIgnoreCase(searchScope))//通过标签、描述搜索
				{
					fields = new String[] { "tags", "description"};
				}
				else if("namedescription".equalsIgnoreCase(searchScope))//通过标题、描述搜索
				{
					fields = new String[] { "name", "description" };
				}
				else//全部搜索：名称，标签，描述
				{
					file = new File(dicstorePath);				//获取同义词库路径文件夹
					FSDirectory dicDir = FSDirectory.open(file);
					if(IndexReader.indexExists(dicDir))
					{
						iReader = IndexReader.open(dicDir,true);//只读模式打开
						iSearcher1 = new IndexSearcher(iReader);		//创建索引查询器
						QueryParser tqp = new QueryParser(Version.LUCENE_34, "name" , analyzer);	//多字段查询
						Query dicstore = tqp.parse(keywords);
						TopDocs tdocs = iSearcher1.search(dicstore,1000000000);		//取同义词库词组
						String dics="";
						if(tdocs.totalHits>0)
						{
							Document doc=iSearcher1.doc(tdocs.scoreDocs[0].doc);
							dics=doc.get("name");
							QueryParser qp1 = new QueryParser(Version.LUCENE_34, "fileName" , ikAnalyzer);	//多字段查询
							QueryParser qp2 = new QueryParser(Version.LUCENE_34, "tags" , ikAnalyzer);	//多字段查询
							QueryParser qp3 = new QueryParser(Version.LUCENE_34, "description" , ikAnalyzer);	//多字段查询
							QueryParser qp4 = new QueryParser(Version.LUCENE_34, "content" , ikAnalyzer);	//内容 多字段查询 （用于实现全文检索）
							QueryParser qp5 = new QueryParser(Version.LUCENE_34, "address" , ikAnalyzer);	//文件地址（标记），客户端拍照上传有此值
							
							Query qName = qp1.parse(dics);
							Query qTags = qp2.parse(dics);
							Query qDescription = qp3.parse(dics);
							Query qCon = qp4.parse(dics);
							Query qAddr = qp5.parse(dics);
							qName.setBoost(0.8f);//权重0.8 (最高)
							qTags.setBoost(0.5f);//权重0.5
							qDescription.setBoost(0.3f);//权重0.3
							qCon.setBoost(0.1f);//权重0.1 (最低)
							qAddr.setBoost(0.2f);
							
							queryShould.add(qName,Occur.SHOULD);
							queryShould.add(qTags,Occur.SHOULD);
							queryShould.add(qDescription,Occur.SHOULD);

							queryShould.add(qCon,Occur.SHOULD);
							queryShould.add(qAddr,Occur.SHOULD);
						}
					}
					fields = new String[] { "fileName", "description", "tags","content"};
				}
				
				QueryParser qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字

				//关键字查询
				Query keywordQuery = qp.parse(keywords);
				keywordQuery.setBoost(1.0f);
				queryShould.add(keywordQuery,Occur.SHOULD);
				
				//关键字拆分 完整匹配：XG15-A427分解成XG 15 A 427等形式
				Query splitQuery = qp.parse(splitKeywords(keywords));
				splitQuery.setBoost(0.8f);
				queryShould.add(splitQuery,Occur.SHOULD);
				
				//关键字模糊匹配：keywords*
				Query blurQuery = qp.parse(blurkey);
				blurQuery.setBoost(0.6f);
				queryShould.add(blurQuery,Occur.SHOULD);
				
				Term userIdTerm = new Term("userId", String.valueOf(userId));				//查询当前用户文件条件
				TermQuery userIdQuery = new TermQuery(userIdTerm);
				BooleanQuery queryMust = new BooleanQuery(); 				//将条件进行组合
				queryMust.add(userIdQuery,Occur.MUST);
				
				BooleanQuery query = new BooleanQuery(); 				//将条件进行组合
				
				
				if(1==1){
					BooleanQuery queryMust2 = new BooleanQuery();
					BooleanQuery queryMust3 = new BooleanQuery();
					BooleanQuery queryShould2 = new BooleanQuery();
					String de_keywords = param.get("de_keywords");
					if(StringUtils.isNotEmpty(de_keywords)){//包含[/-]
						String []keys1 = de_keywords.split(" ");// [ ]
						for(int i=0;i<keys1.length;i++){
							queryMust2 = new BooleanQuery();
							String key = keys1[i];
							if(key.indexOf("/+")!=-1){
 								key = key.replace("/+", "");
 							}
 							if(key.indexOf("+")!=-1){
 								key = key.replace("+", "");
 							}
 							if(key.indexOf("/-")!=-1){
 								key = key.replace("/-", "");
 							}
 							if(key.indexOf("-")!=-1){
 								key = key.replace("-", "");
 							}
 							key = key.trim().toLowerCase();
 							if(StringUtils.isNotEmpty(key)){
 								Term fileNameTerm = new Term("fileName", "*"+key+"*");
	 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
	 							Term descTerm = new Term("description", "*"+key+"*");
	 							WildcardQuery descQuery = new WildcardQuery(descTerm);
	 							Term tagsTerm = new Term("tags", "*"+key+"*");
	 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);
	 							
	 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
	 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
	 							
	 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
 								tagsQuery.setBoost(0.5f);//权重0.5
								descQuery.setBoost(0.3f);//权重0.3
								contentQuery.setBoost(0.1f);//权重0.1 (最低)
	 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
	 							{
	 								queryMust2.add(fileNameQuery,Occur.SHOULD);
	 							}
	 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
	 							{
	 								queryMust2.add(tagsQuery,Occur.SHOULD);
	 							}else{
		 							queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							queryMust2.add(descQuery,Occur.SHOULD);
		 							queryMust2.add(tagsQuery,Occur.SHOULD);
		 							queryMust2.add(contentQuery,Occur.SHOULD);
	 							}
	 							queryShould.add(queryMust2,Occur.MUST_NOT);	 							
 							}
						}
						
					}
					queryMust2 = new BooleanQuery();
					String splitwords = splitKeywords2(keywords);
					String keywords2 = splitwords.trim();
//					if(keywords2.indexOf("/-")!=-1){
//						keywords2 = keywords2.replaceAll("/-", " ");
//					}
//					if(keywords2.indexOf("-")!=-1){
//						keywords2 = keywords2.replaceAll("-", " ");
//					}
//					if(keywords2.indexOf("/+")!=-1){
//						keywords2 = keywords2.replaceAll("/+", "+");
//					}
					while(true){
						keywords2 = keywords2.trim();
						if(keywords2.startsWith("+")){
							keywords2 = keywords2.substring(1);
						}else if(keywords2.endsWith("+")){
							keywords2 = keywords2.substring(0,keywords2.length()-1);
						}else{
							break;
						}
					}
					while(true){
						int num = keywords2.indexOf(" +");
						if(num!=-1){
							String str1 = keywords2.substring(0, num);
							String str2 = keywords2.substring(num+1);
							keywords2 = str1+"/"+str2;
						}else{
							break;
						}
					}
					//排除最前和最后为+号的情况
 					String []keys2 = keywords2.split("/+");// [ ]
					if(keys2.length>0){
						String []keys3 = keys2[0].split(" ");
						if(keys3!=null && keys3.length>0){
							//不管有没有[ ],都必须查到数据 &&（a ||b ||c）
							//要考虑分词的情况，改为or
							queryShould2 = new BooleanQuery();
							queryMust2 = new BooleanQuery();
							for(int i=0;i<keys3.length;i++){
								String key = keys3[i];
								if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
								key = key.trim().toLowerCase();
								
								if(StringUtils.isNotEmpty(key)){
									Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(fileNameQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(tagsQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}else{
		 								if(keys2.length>1){
			 								queryMust2.add(fileNameQuery,Occur.SHOULD);
			 								queryMust2.add(descQuery,Occur.SHOULD);
			 								queryMust2.add(tagsQuery,Occur.SHOULD);
			 								queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								queryShould2.add(descQuery,Occur.SHOULD);
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 							}
								}
							}
							queryShould.add(queryShould2,Occur.SHOULD);
							if(keys2.length>1){
								//存在加号时是必须的
								queryShould.add(queryMust3,Occur.MUST);
							}
						}
						
						if(keys2.length>1){//包含[/+]   //&& a && b && c
							for(int i=1;i<keys2.length;i++){
								queryMust2 = new BooleanQuery();
								String key = keys2[i];
	 							if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
	 							key = key.trim().toLowerCase();
	 							if(StringUtils.isNotEmpty(key)){
		 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryMust2.add(tagsQuery,Occur.SHOULD);
		 							}else{
			 							queryMust2.add(fileNameQuery,Occur.SHOULD);
			 							queryMust2.add(descQuery,Occur.SHOULD);
			 							queryMust2.add(tagsQuery,Occur.SHOULD);
			 							queryMust2.add(contentQuery,Occur.SHOULD);
		 							}
		 							queryShould.add(queryMust2,Occur.MUST);
	 							}
							}
						}
					}
				}
				
				query.add(queryMust,Occur.MUST);
				query.add(queryShould,Occur.MUST);
				
				BooleanQuery fileNameBQ = null;
				BooleanQuery suffixBQ = null;
				BooleanQuery addTimeBQ = null;
				BooleanQuery updateDateBQ = null;
				BooleanQuery sizeBQ = null;
				//添加搜索选项
				addTerm(qp,query,fileNameBQ,suffixBQ,addTimeBQ,updateDateBQ,sizeBQ,param);
				TopDocs topDocs = iSearcher.search(query,1000000000); 		//查询1000000000条记录，表示所有
				logger.debug("Lucene检索我的文件,nowPage="+nowPage+",pageSize="+pageSize+",keywords="+keywords+" ,topDocs.hit"+topDocs.totalHits);
				String hight = "";
				if(!StringUtils.equals("false", param.get("highlightEnabled")))//启用关键字高亮度显示
				{
					hight = keywords;
				}
				page=pageService.initParameter(topDocs, iSearcher, "my", nowPage, pageSize,hight);
			}
			catch(Exception e)
			{
				logger.error("Lucene异常：",e);
			}
			finally
			{
				//关闭打开的对象，这里很重要，否则容易内存溢出
				try {
					if(null != iReader) 
						iReader.close();
					if(null != iSearcher)
						iSearcher.close();
					if(null != iSearcher1)
						iSearcher1.close();
					if(null != analyzer)
						analyzer.close();
					if(null != ikAnalyzer)
						ikAnalyzer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return page;
	}
	
	/**
	 * 搜索 我共享出去的文件 
	 * @param keywords
	 * @param userId
	 * @param pageService
	 * @param nowPage
	 * @param pageSize
	 * @param param
	 * @return
	 */
	public static Page searchShareFiles(String keywords,int userId,PageService pageService,int nowPage, int pageSize, Map<String,String> param)
	{
		Page page = new Page();
		if(StringUtils.isNotEmpty(keywords))
		{
			keywords = filterKeywords(keywords);
			String blurkey = getBlurkey(keywords);
//			
			IndexReader iReader = null;
			IndexSearcher iSearcher = null;
			IndexSearcher iSearcher1 = null;
			Analyzer analyzer = null;
			Analyzer ikAnalyzer = null;
			try
			{
				File file = new File(shareLucenePath);				//获取lucene文件夹
				
				FSDirectory dir = FSDirectory.open(file);
				if(IndexReader.indexExists(dir))
				{
					iReader = IndexReader.open(dir,true);//只读模式打开
				}
				else
				{
					return page;
				}
				
				String searchScope = param.get("scope");
				iSearcher = new IndexSearcher(iReader);		//创建索引查询器
				analyzer = new IKAnalyzer();//使用IK分词器
				ikAnalyzer = new IKAnalyzer(true);//使用IK智能分词器
				BooleanQuery queryShould = new BooleanQuery(); 				//将条件进行组合
				
				String[] fields = null;
				if("name".equalsIgnoreCase(searchScope))//通过文件名搜索
				{
					fields = new String[] { "fileName" };
				}
				else if("tag".equalsIgnoreCase(searchScope))//通过标签搜索
				{
					fields = new String[] { "tags" };
				}
				else//搜索全部
				{
					file = new File(dicstorePath);				//获取同义词库文件夹
					FSDirectory dicDir = FSDirectory.open(file);
					if(IndexReader.indexExists(dicDir))
					{
						iReader = IndexReader.open(dicDir,true);//只读模式打开
						iSearcher1 = new IndexSearcher(iReader);		//创建索引查询器
						QueryParser tqp = new QueryParser(Version.LUCENE_34, "name" , analyzer);	//多字段查询
						Query dicstore = tqp.parse(keywords);
						TopDocs tdocs = iSearcher1.search(dicstore,1000000000);		//取同义词库词组
						String dics="";
						if(tdocs.totalHits>0)
						{
							Document doc=iSearcher1.doc(tdocs.scoreDocs[0].doc);
							dics=doc.get("name");
							QueryParser qp1 = new QueryParser(Version.LUCENE_34, "fileName" , ikAnalyzer);	//多字段查询
							QueryParser qp2 = new QueryParser(Version.LUCENE_34, "tags" , ikAnalyzer);	//多字段查询
							QueryParser qp3 = new QueryParser(Version.LUCENE_34, "description" , ikAnalyzer);	//多字段查询
							QueryParser qp4 = new QueryParser(Version.LUCENE_34, "content" , ikAnalyzer);	//内容 多字段查询 （用于实现全文检索）
							
							Query qName = qp1.parse(dics);
							Query qTags = qp2.parse(dics);
							Query qDescription = qp3.parse(dics);
							Query qCon = qp4.parse(dics);
							qName.setBoost(0.8f);//权重0.8 (最高)
							qTags.setBoost(0.5f);//权重0.5
							qDescription.setBoost(0.3f);//权重0.3
							qCon.setBoost(0.1f);//权重0.1 (最低)
							
							queryShould.add(qName,Occur.SHOULD);
							queryShould.add(qTags,Occur.SHOULD);
							queryShould.add(qDescription,Occur.SHOULD);

							queryShould.add(qCon,Occur.SHOULD);
						}
					}
					fields = new String[] { "fileName", "description", "tags","content"};
				}
				
				QueryParser qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字

				//关键字查询
				Query keywordQuery = qp.parse(keywords);
				keywordQuery.setBoost(1.0f);
				queryShould.add(keywordQuery,Occur.SHOULD);
				
				//关键字拆分 完整匹配：XG15-A427分解成XG 15 A 427等形式
				Query splitQuery = qp.parse(splitKeywords(keywords));
				splitQuery.setBoost(0.8f);
				queryShould.add(splitQuery,Occur.SHOULD);
				
				//关键字模糊匹配：keywords*
				Query blurQuery = qp.parse(blurkey);
				blurQuery.setBoost(0.6f);
				queryShould.add(blurQuery,Occur.SHOULD);
				
				Term userIdTerm = new Term("userId", String.valueOf(userId));				//查询当前用户文件条件
				TermQuery userIdQuery = new TermQuery(userIdTerm);
				BooleanQuery queryMust = new BooleanQuery(); 				//将条件进行组合
				queryMust.add(userIdQuery,Occur.MUST);
				
				BooleanQuery query = new BooleanQuery(); 				//将条件进行组合
				
				
				if(1==1){
					BooleanQuery queryMust2 = new BooleanQuery();
					BooleanQuery queryMust3 = new BooleanQuery();
					BooleanQuery queryShould2 = new BooleanQuery();
					String de_keywords = param.get("de_keywords");
					if(StringUtils.isNotEmpty(de_keywords)){//包含[/-]
						String []keys1 = de_keywords.split(" ");// [ ]
						for(int i=0;i<keys1.length;i++){
							queryMust2 = new BooleanQuery();
							String key = keys1[i];
							if(key.indexOf("/+")!=-1){
 								key = key.replace("/+", "");
 							}
 							if(key.indexOf("+")!=-1){
 								key = key.replace("+", "");
 							}
 							if(key.indexOf("/-")!=-1){
 								key = key.replace("/-", "");
 							}
 							if(key.indexOf("-")!=-1){
 								key = key.replace("-", "");
 							}
 							key = key.trim().toLowerCase();
 							if(StringUtils.isNotEmpty(key)){
	 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
	 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
	 							Term descTerm = new Term("description", "*"+key+"*");
	 							WildcardQuery descQuery = new WildcardQuery(descTerm);
	 							Term tagsTerm = new Term("tags", "*"+key+"*");
	 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

	 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
	 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
	 							
	 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
 								tagsQuery.setBoost(0.5f);//权重0.5
								descQuery.setBoost(0.3f);//权重0.3
								contentQuery.setBoost(0.1f);//权重0.1 (最低)
	 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
	 							{
	 								queryMust2.add(fileNameQuery,Occur.SHOULD);
	 							}
	 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
	 							{
	 								queryMust2.add(tagsQuery,Occur.SHOULD);
	 							}else{
		 							queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							queryMust2.add(descQuery,Occur.SHOULD);
		 							queryMust2.add(tagsQuery,Occur.SHOULD);
		 							queryMust2.add(contentQuery,Occur.SHOULD);
	 							}
	 							queryShould.add(queryMust2,Occur.MUST_NOT);
 							}
						}
						
					}
					queryMust2 = new BooleanQuery();
					String splitwords = splitKeywords2(keywords);
					String keywords2 = splitwords.trim();
//					if(keywords2.indexOf("/-")!=-1){
//						keywords2 = keywords2.replaceAll("/-", " ");
//					}
//					if(keywords2.indexOf("-")!=-1){
//						keywords2 = keywords2.replaceAll("-", " ");
//					}
//					if(keywords2.indexOf("/+")!=-1){
//						keywords2 = keywords2.replaceAll("/+", "+");
//					}
					while(true){
						keywords2 = keywords2.trim();
						if(keywords2.startsWith("+")){
							keywords2 = keywords2.substring(1);
						}else if(keywords2.endsWith("+")){
							keywords2 = keywords2.substring(0,keywords2.length()-1);
						}else{
							break;
						}
					}
					while(true){
						int num = keywords2.indexOf(" +");
						if(num!=-1){
							String str1 = keywords2.substring(0, num);
							String str2 = keywords2.substring(num+1);
							keywords2 = str1+"/"+str2;
						}else{
							break;
						}
					}
					//排除最前和最后为+号的情况
 					String []keys2 = keywords2.split("/+");// [ ]
					if(keys2.length>0){
						String []keys3 = keys2[0].split(" ");
						if(keys3!=null && keys3.length>0){
							//不管有没有[ ],都必须查到数据 &&（a ||b ||c）
							//要考虑分词的情况，改为or
							queryShould2 = new BooleanQuery();
							queryMust2 = new BooleanQuery();
							for(int i=0;i<keys3.length;i++){
								String key = keys3[i];
								if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
								key = key.trim().toLowerCase();
								
								if(StringUtils.isNotEmpty(key)){
									Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(fileNameQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(tagsQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}else{
		 								if(keys2.length>1){
			 								queryMust2.add(fileNameQuery,Occur.SHOULD);
			 								queryMust2.add(descQuery,Occur.SHOULD);
			 								queryMust2.add(tagsQuery,Occur.SHOULD);
			 								queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								queryShould2.add(descQuery,Occur.SHOULD);
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 							}
								}
							}
							queryShould.add(queryShould2,Occur.SHOULD);
							if(keys2.length>1){
								//存在加号时是必须的
								queryShould.add(queryMust3,Occur.MUST);
							}
						}
						
						if(keys2.length>1){//包含[/+]   //&& a && b && c
							for(int i=1;i<keys2.length;i++){
								queryMust2 = new BooleanQuery();
								String key = keys2[i];
	 							if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
	 							key = key.trim().toLowerCase();
	 							if(StringUtils.isNotEmpty(key)){
		 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);
		 							
		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryMust2.add(tagsQuery,Occur.SHOULD);
		 							}else{
			 							queryMust2.add(fileNameQuery,Occur.SHOULD);
			 							queryMust2.add(descQuery,Occur.SHOULD);
			 							queryMust2.add(tagsQuery,Occur.SHOULD);
			 							queryMust2.add(contentQuery,Occur.SHOULD);
		 							}
		 							queryShould.add(queryMust2,Occur.MUST);
	 							}
							}
						}
					}
				}
				
				query.add(queryMust,Occur.MUST);
				query.add(queryShould,Occur.MUST);
				
				BooleanQuery fileNameBQ = null;
				BooleanQuery suffixBQ = null;
				BooleanQuery addTimeBQ = null;
				BooleanQuery updateDateBQ = null;
				BooleanQuery sizeBQ = null;
				//添加搜索选项
				addTerm(qp,query,fileNameBQ,suffixBQ,addTimeBQ,updateDateBQ,sizeBQ,param);
				TopDocs topDocs = iSearcher.search(query,1000000000); 		//查询1000000000条记录，表示所有
				logger.debug("Lucene检索共享文件,nowPage="+nowPage+",pageSize="+pageSize+",keywords="+keywords+" ,topDocs.hit"+topDocs.totalHits);
				String hight = "";//移动端接口使用此处理避免返回带font标签red样式的文件名
				if(!StringUtils.equals("false", param.get("highlightEnabled")))//启用关键字高亮度显示
				{
					hight = keywords;
				}
				page=pageService.initParameter(topDocs, iSearcher,"share" , nowPage, pageSize, hight);
			}
			catch(Exception e)
			{
				logger.error("Lucene异常：",e);
			}
			finally
			{
				//关闭打开的对象，这里很重要，否则容易内存溢出
				try {
					if(null != iReader) 
						iReader.close();
					if(null != iSearcher)
						iSearcher.close();
					if(null != iSearcher1)
						iSearcher1.close();
					if(null != analyzer)
						analyzer.close();
					if(null != ikAnalyzer)
						ikAnalyzer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return page;
	}
	
	/**
	 * 搜索 我收到的共享
	 * @param keywords
	 * @param userId
	 * @param pageService
	 * @param nowPage
	 * @param pageSize
	 * @param param
	 * @return
	 */
	public static Page searchMyShareFiles(String keywords,int userId,PageService pageService,int nowPage, int pageSize, Map<String,String> param)
	{
		Page page = new Page();
		if(StringUtils.isNotEmpty(keywords))
		{
			keywords = filterKeywords(keywords);
			String blurkey = getBlurkey(keywords);
//			
			IndexReader iReader = null;
			IndexSearcher iSearcher = null;
			IndexSearcher iSearcher1 = null;
			Analyzer analyzer = null;
			Analyzer ikAnalyzer = null;
			try
			{
				File file = new File(shareLucenePath);				//获取lucene文件夹
				FSDirectory dir = FSDirectory.open(file);
				if(IndexReader.indexExists(dir))
				{
					iReader = IndexReader.open(dir,true);//只读模式打开
				}
				else
				{
					return page;
				}
				iSearcher = new IndexSearcher(iReader);		//创建索引查询器
				analyzer = new IKAnalyzer();//使用IK分词器
				ikAnalyzer = new IKAnalyzer(true);//使用IK智能分词器
				BooleanQuery queryShould = new BooleanQuery(); 				//将条件进行组合
				
				String searchScope = param.get("scope");
				String[] fields = null;
				if("name".equalsIgnoreCase(searchScope))//通过文件名搜索
				{
					fields = new String[] { "fileName" };
				}
				else if("tag".equalsIgnoreCase(searchScope))//通过标签搜索
				{
					fields = new String[] { "tags" };
				}
				else//搜索全部
				{
					file = new File(dicstorePath);				//获取同义词库文件夹
					FSDirectory dicDir = FSDirectory.open(file);
					if(IndexReader.indexExists(dicDir))
					{
						iReader = IndexReader.open(dicDir,true);//只读模式打开
						iSearcher1 = new IndexSearcher(iReader);		//创建索引查询器
						QueryParser tqp = new QueryParser(Version.LUCENE_34, "name" , analyzer);	//多字段查询
						Query dicstore = tqp.parse(keywords);
						TopDocs tdocs = iSearcher1.search(dicstore,1000000000);		//取同义词库词组
						String dics="";
						if(tdocs.totalHits>0)
						{
							Document doc=iSearcher1.doc(tdocs.scoreDocs[0].doc);
							dics=doc.get("name");
							QueryParser qp1 = new QueryParser(Version.LUCENE_34, "fileName" , ikAnalyzer);	//多字段查询
							QueryParser qp2 = new QueryParser(Version.LUCENE_34, "tags" , ikAnalyzer);	//多字段查询
							QueryParser qp3 = new QueryParser(Version.LUCENE_34, "description" , ikAnalyzer);	//多字段查询
							QueryParser qp4 = new QueryParser(Version.LUCENE_34, "content" , ikAnalyzer);	//内容 多字段查询 （用于实现全文检索）
							
							Query qName = qp1.parse(dics);
							Query qTags = qp2.parse(dics);
							Query qDescription = qp3.parse(dics);
							Query qCon = qp4.parse(dics);
							qName.setBoost(0.8f);//权重0.8 (最高)
							qTags.setBoost(0.5f);//权重0.5
							qDescription.setBoost(0.3f);//权重0.3
							qCon.setBoost(0.1f);//权重0.1 (最低)
							
							queryShould.add(qName,Occur.SHOULD);
							queryShould.add(qTags,Occur.SHOULD);
							queryShould.add(qDescription,Occur.SHOULD);

							queryShould.add(qCon,Occur.SHOULD);
						}
					}
					fields = new String[] { "fileName", "description", "tags","content"};
				}
				
				QueryParser qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字

				//关键字查询
				Query keywordQuery = qp.parse(keywords);
				keywordQuery.setBoost(1.0f);
				queryShould.add(keywordQuery,Occur.SHOULD);
				
				//关键字拆分 完整匹配：XG15-A427分解成XG 15 A 427等形式
				Query splitQuery = qp.parse(splitKeywords(keywords));
				splitQuery.setBoost(0.8f);
				queryShould.add(splitQuery,Occur.SHOULD);
				
				//关键字模糊匹配：keywords*
				Query blurQuery = qp.parse(blurkey);
				blurQuery.setBoost(0.6f);
				queryShould.add(blurQuery,Occur.SHOULD);
				
				Term userIdTerm = new Term("receiveUserIds", String.valueOf(userId));				//查询当前用户文件条件
				TermQuery userIdQuery = new TermQuery(userIdTerm);
				
				BooleanQuery queryMust = new BooleanQuery(); 				//将条件进行组合
				queryMust.add(userIdQuery,Occur.MUST);
				
				BooleanQuery query = new BooleanQuery(); 				//将条件进行组合
				
				
				if(1==1){
					BooleanQuery queryMust2 = new BooleanQuery();
					BooleanQuery queryMust3 = new BooleanQuery();
					BooleanQuery queryShould2 = new BooleanQuery();
					String de_keywords = param.get("de_keywords");
					if(StringUtils.isNotEmpty(de_keywords)){//包含[/-]
						String []keys1 = de_keywords.split(" ");// [ ]
						for(int i=0;i<keys1.length;i++){
							queryMust2 = new BooleanQuery();
							String key = keys1[i];
							if(key.indexOf("/+")!=-1){
 								key = key.replace("/+", "");
 							}
 							if(key.indexOf("+")!=-1){
 								key = key.replace("+", "");
 							}
 							if(key.indexOf("/-")!=-1){
 								key = key.replace("/-", "");
 							}
 							if(key.indexOf("-")!=-1){
 								key = key.replace("-", "");
 							}
 							key = key.trim().toLowerCase();
 							if(StringUtils.isNotEmpty(key)){
	 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
	 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
	 							Term descTerm = new Term("description", "*"+key+"*");
	 							WildcardQuery descQuery = new WildcardQuery(descTerm);
	 							Term tagsTerm = new Term("tags", "*"+key+"*");
	 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

	 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
	 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
	 							
	 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
 								tagsQuery.setBoost(0.5f);//权重0.5
								descQuery.setBoost(0.3f);//权重0.3
								contentQuery.setBoost(0.1f);//权重0.1 (最低)
	 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
	 							{
	 								queryMust2.add(fileNameQuery,Occur.SHOULD);
	 							}
	 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
	 							{
	 								queryMust2.add(tagsQuery,Occur.SHOULD);
	 							}else{
		 							queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							queryMust2.add(descQuery,Occur.SHOULD);
		 							queryMust2.add(tagsQuery,Occur.SHOULD);
		 							queryMust2.add(contentQuery,Occur.SHOULD);
	 							}
	 							queryShould.add(queryMust2,Occur.MUST_NOT);
 							}
						}
						
					}
					queryMust2 = new BooleanQuery();
					String splitwords = splitKeywords2(keywords);
					String keywords2 = splitwords.trim();
//					if(keywords2.indexOf("/-")!=-1){
//						keywords2 = keywords2.replaceAll("/-", " ");
//					}
//					if(keywords2.indexOf("-")!=-1){
//						keywords2 = keywords2.replaceAll("-", " ");
//					}
//					if(keywords2.indexOf("/+")!=-1){
//						keywords2 = keywords2.replaceAll("/+", "+");
//					}
					while(true){
						keywords2 = keywords2.trim();
						if(keywords2.startsWith("+")){
							keywords2 = keywords2.substring(1);
						}else if(keywords2.endsWith("+")){
							keywords2 = keywords2.substring(0,keywords2.length()-1);
						}else{
							break;
						}
					}
					while(true){
						int num = keywords2.indexOf(" +");
						if(num!=-1){
							String str1 = keywords2.substring(0, num);
							String str2 = keywords2.substring(num+1);
							keywords2 = str1+"/"+str2;
						}else{
							break;
						}
					}
					//排除最前和最后为+号的情况
 					String []keys2 = keywords2.split("/+");// [ ]
					if(keys2.length>0){
						String []keys3 = keys2[0].split(" ");
						if(keys3!=null && keys3.length>0){
							//不管有没有[ ],都必须查到数据 &&（a ||b ||c）
							//要考虑分词的情况，改为or
							queryShould2 = new BooleanQuery();
							queryMust2 = new BooleanQuery();
							for(int i=0;i<keys3.length;i++){
								String key = keys3[i];
								if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
								key = key.trim().toLowerCase();
								if(StringUtils.isNotEmpty(key)){
									Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);
		 							
		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(fileNameQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(tagsQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}else{
		 								if(keys2.length>1){
			 								queryMust2.add(fileNameQuery,Occur.SHOULD);
			 								queryMust2.add(descQuery,Occur.SHOULD);
			 								queryMust2.add(tagsQuery,Occur.SHOULD);
			 								queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								queryShould2.add(descQuery,Occur.SHOULD);
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 							}
								}
							}
							queryShould.add(queryShould2,Occur.SHOULD);
							if(keys2.length>1){
								//存在加号时是必须的
								queryShould.add(queryMust3,Occur.MUST);
							}
						}
						
						if(keys2.length>1){//包含[/+]   //&& a && b && c
							for(int i=1;i<keys2.length;i++){
								queryMust2 = new BooleanQuery();
								String key = keys2[i];
	 							if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
	 							key = key.trim().toLowerCase();
	 							if(StringUtils.isNotEmpty(key)){
		 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);
		 							
		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryMust2.add(tagsQuery,Occur.SHOULD);
		 							}else{
			 							queryMust2.add(fileNameQuery,Occur.SHOULD);
			 							queryMust2.add(descQuery,Occur.SHOULD);
			 							queryMust2.add(tagsQuery,Occur.SHOULD);
			 							queryMust2.add(contentQuery,Occur.SHOULD);
		 							}
		 							queryShould.add(queryMust2,Occur.MUST);
	 							}
							}
						}
					}
				}
				
				query.add(queryMust,Occur.MUST);
				query.add(queryShould,Occur.MUST);
				
				BooleanQuery fileNameBQ = null;
				BooleanQuery suffixBQ = null;
				BooleanQuery addTimeBQ = null;
				BooleanQuery updateDateBQ = null;
				BooleanQuery sizeBQ = null;
				//添加搜索选项
				addTerm(qp,query,fileNameBQ,suffixBQ,addTimeBQ,updateDateBQ,sizeBQ,param);
				TopDocs topDocs = iSearcher.search(query,1000000000); 		//查询1000000000条记录，表示所有
				logger.debug("Lucene检索收到的共享文件,nowPage="+nowPage+",pageSize="+pageSize+",keywords="+keywords+" ,topDocs.hit"+topDocs.totalHits);
				String hight = "";//移动端接口使用此处理避免返回带font标签red样式的文件名
				if(!StringUtils.equals("false", param.get("highlightEnabled")))//启用关键字高亮度显示
				{
					hight = keywords;
				}
				page=pageService.initParameter(topDocs, iSearcher,"receive", nowPage, pageSize, hight);
			}
			catch(Exception e)
			{
				logger.error("Lucene异常：",e);
			}
			finally
			{
				//关闭打开的对象，这里很重要，否则容易内存溢出
				try {
					if(null != iReader) 
						iReader.close();
					if(null != iSearcher)
						iSearcher.close();
					if(null != iSearcher1)
						iSearcher1.close();
					if(null != analyzer)
						analyzer.close();
					if(null != ikAnalyzer)
						ikAnalyzer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return page;
	}
	
	public static Page searchCommonFiles(String keywords,User user, PageService pageService ,BaseService service ,int nowPage, int pageSize, Map<String,String> param)
	{
		Page page= new Page();
		if(StringUtils.isNotEmpty(keywords))
		{
			keywords = filterKeywords(keywords);
			String blurkey = getBlurkey(keywords);
//			
			IndexReader dicReader = null;
			IndexSearcher iSearcher1 = null;
			IndexSearcher iSearcher = null;
			Analyzer analyzer = null;
			Analyzer ikAnalyzer = null;
			try
			{
				String searchScope = param.get("scope");
				BooleanQuery queryShould = new BooleanQuery(); 				//将条件进行组合
				
				analyzer = new IKAnalyzer();//使用IK分词器
				String[] fields = null;
				if("name".equalsIgnoreCase(searchScope))//通过标题搜索
				{
					fields = new String[] { "fileName" };
				}
				else if("tag".equalsIgnoreCase(searchScope))//通过标签搜索
				{
					fields = new String[] { "tags" };
				}
				else if("description".equalsIgnoreCase(searchScope))//通过描述搜索
				{
					fields = new String[] { "description" };
				}
				else if("nametag".equalsIgnoreCase(searchScope))//通过标签、标题搜索
				{
					fields = new String[] { "fileName", "tags"};
				}
				else if("tagdescription".equalsIgnoreCase(searchScope))//通过标签、描述搜索
				{
					fields = new String[] { "tags", "description"};
				}
				else if("namedescription".equalsIgnoreCase(searchScope))//通过标题、描述搜索
				{
					fields = new String[] { "name", "description" };
				}				
				else//搜索全部
				{
					File dicstoreFile = new File(dicstorePath);				//获取lucene文件夹
					FSDirectory dicDir = FSDirectory.open(dicstoreFile);
					if(IndexReader.indexExists(dicDir))
					{
						dicReader = IndexReader.open(dicDir,true);//只读模式打开
						iSearcher1 = new IndexSearcher(dicReader);		//创建索引查询器
						ikAnalyzer = new IKAnalyzer(true);//使用IK智能分词器
						
						QueryParser tqp = new QueryParser(Version.LUCENE_34, "name" , analyzer);	//多字段查询
						Query dicstore = tqp.parse(keywords);
						TopDocs tdocs = iSearcher1.search(dicstore,1000000000);		//取同义词库词组
						String dics="";
						if(tdocs.totalHits>0)
						{
							Document doc=iSearcher1.doc(tdocs.scoreDocs[0].doc);
							dics=doc.get("name");
							QueryParser qp1 = new QueryParser(Version.LUCENE_34, "fileName" , ikAnalyzer);	//单字段查询
							QueryParser qp2 = new QueryParser(Version.LUCENE_34, "tags" , ikAnalyzer);	//单字段查询
							QueryParser qp3 = new QueryParser(Version.LUCENE_34, "description" , ikAnalyzer);	//单字段查询
							QueryParser qp4 = new QueryParser(Version.LUCENE_34, "content" , ikAnalyzer);	//内容 多字段查询 （用于实现全文检索）
							QueryParser qp5 = new QueryParser(Version.LUCENE_34, "address" , ikAnalyzer);	//文件地址（标记），客户端拍照上传有此值
							
							Query qName = qp1.parse(dics);
							Query qTags = qp2.parse(dics);
							Query qDescription = qp3.parse(dics);
							Query qCon = qp4.parse(dics);
							Query qAddr = qp5.parse(dics);
							qName.setBoost(0.8f);//权重0.8 (最高)
							qTags.setBoost(0.5f);//权重0.5
							qDescription.setBoost(0.3f);//权重0.3
							qCon.setBoost(0.1f);//权重0.1 (最低)
							qAddr.setBoost(0.2f);
							
							queryShould.add(qName,Occur.SHOULD);
							queryShould.add(qTags,Occur.SHOULD);
							queryShould.add(qDescription,Occur.SHOULD);

							queryShould.add(qCon,Occur.SHOULD);
							queryShould.add(qAddr,Occur.SHOULD);
							//扩展属性搜索
							//TODO ，暂不需要做
						}
					}
					fields = new String[] { "fileName", "description", "tags","content"};
				}
				
				QueryParser qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字

				//关键字查询
				Query keywordQuery = qp.parse(keywords);
				keywordQuery.setBoost(1.0f);
				queryShould.add(keywordQuery,Occur.SHOULD);
				
				//关键字拆分 完整匹配：XG15-A427分解成XG 15 A 427等形式
				Query splitQuery = qp.parse(splitKeywords(keywords));
				splitQuery.setBoost(0.8f);
				queryShould.add(splitQuery,Occur.SHOULD);
				
				//关键字模糊匹配：keywords*
				Query blurQuery = qp.parse(blurkey);
				blurQuery.setBoost(0.6f);
				queryShould.add(blurQuery,Occur.SHOULD);
				
				BooleanQuery queryMust = new BooleanQuery(); 				//将条件进行组合
				int userId = 0;
				boolean isAdmin =false;
				if(null != user)
				{
					userId = user.getId();
					if(0==user.getRole().getType())
					{
						isAdmin = true;
					}
				}
				if(!isAdmin)
				{
					logger.debug("用户权限查看资料库资料");
					//所属部门 ( 包括子部门)id
					String d_ids = null;	//部门id
					String g_ids = null;	//工作组id
					if(null != user)
					{
//						Department department = user.getDepartment();
						Department department = (Department) service.queryByPK(Department.class, user.getDepartmentId());
						if (null != department)
						{
							d_ids = department.getPath();
						}
						g_ids = user.getGroupIds();
					}
					
					// 查询 用户权限不是继承上级的文件
					StringBuffer qStr=new StringBuffer();
					qStr.append(" SELECT * FROM (SELECT * FROM jweb_systemrole_commonfile cf ");
					qStr.append(" WHERE cf.role_value<>'n' AND");
					qStr.append(" (cf.role_type=0 AND cf.role_id = "+userId+")");
					if(StringUtils.isNotEmpty(g_ids))
					{
						qStr.append(" OR (cf.role_type=1 AND cf.role_id IN ("+g_ids+"))");
					}
					if(StringUtils.isNotEmpty(d_ids))
					{
						qStr.append(" OR (cf.role_type=2 AND cf.role_id  IN( "+d_ids+"))");
					}
					qStr.append(" AND cf.role_value<>'n' GROUP BY cf.file_id) d , jweb_commonfilecache cfc ");
					qStr.append(" WHERE d.file_id=cfc.id");
					qStr.append(" AND cfc.status>-1");
					List list=service.queryBySql(qStr.toString());
					String fileId="";
					if(null != list && list.size()>0){
						for(int i=0;i<list.size();i++)
						{
							Map m=(Map)list.get(i);
							fileId+=String.valueOf(m.get("file_id"))+",";
						}
					}
					
					
					if(1==1){
						// 查询 资料库的资料库管理员
						qStr=new StringBuffer();
						qStr.append(" SELECT * FROM (SELECT * FROM jweb_systemrole_commonfile cf ");
						qStr.append(" WHERE cf.role_value<>'n' AND");
						qStr.append(" cf.role_type=4 AND cf.role_id = "+userId);
						qStr.append(" GROUP BY cf.file_id) d , jweb_commonfilecache cfc ");
						qStr.append(" WHERE d.file_id=cfc.pid1");
						qStr.append(" AND cfc.id=cfc.pid1");
						qStr.append(" AND cfc.status>-1");
						list=service.queryBySql(qStr.toString());
						//System.out.println("fileId:"+fileId);
						if(null != list && list.size()>0){
							for(int i=0;i<list.size();i++)
							{
								Map m=(Map)list.get(i);
								String str = String.valueOf(m.get("file_id"))+",";
								if((","+fileId).indexOf(","+str)==-1){
									fileId+=str;
								}
							}
						}
						//这里搜索的是用户、资料库管理员对应的目录Id和已设置的权限对应的Id(权限为继承上级的文件不用搜索)
					}
					//System.out.println(fileId.length()+" :"+fileId);
					if(fileId.length()>0)
					{
						fileId=fileId.substring(0, fileId.length()-1);
					}
					logger.debug("fileId="+fileId);
					if(StringUtil.isEmpty(fileId)){
						return page;
					}
					QueryParser pidQp = new MultiFieldQueryParser(Version.LUCENE_34, new String[]{"permissionsFileId"}, analyzer);	//单字段查询
					Query pidLikeQuery = pidQp.parse(fileId);
					queryMust.add(pidLikeQuery,Occur.MUST);
				}
				
				//列表搜索query
				BooleanQuery query = new BooleanQuery(); 				//将条件进行组合
				if(!isAdmin)
				{
					query.add(queryMust,Occur.MUST);
				}
				
				if(1==1){
					BooleanQuery queryMust2 = new BooleanQuery();
					BooleanQuery queryMust3 = new BooleanQuery();
					BooleanQuery queryShould2 = new BooleanQuery();
					String de_keywords = param.get("de_keywords");
					if(StringUtils.isNotEmpty(de_keywords)){//包含[/-]
						String []keys1 = de_keywords.split(" ");// [ ]
						for(int i=0;i<keys1.length;i++){
							queryMust2 = new BooleanQuery();
							String key = keys1[i];
							if(key.indexOf("/+")!=-1){
 								key = key.replace("/+", "");
 							}
 							if(key.indexOf("+")!=-1){
 								key = key.replace("+", "");
 							}
 							if(key.indexOf("/-")!=-1){
 								key = key.replace("/-", "");
 							}
 							if(key.indexOf("-")!=-1){
 								key = key.replace("-", "");
 							}
 							key = key.trim().toLowerCase();
 							if(StringUtils.isNotEmpty(key)){
	 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
	 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
	 							Term descTerm = new Term("description", "*"+key+"*");
	 							WildcardQuery descQuery = new WildcardQuery(descTerm);
	 							Term tagsTerm = new Term("tags", "*"+key+"*");
	 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

	 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
	 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
	 							
	 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
 								tagsQuery.setBoost(0.5f);//权重0.5
								descQuery.setBoost(0.3f);//权重0.3
								contentQuery.setBoost(0.1f);//权重0.1 (最低)
	 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
	 							{
	 								queryMust2.add(fileNameQuery,Occur.SHOULD);
	 							}
	 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
	 							{
	 								queryMust2.add(tagsQuery,Occur.SHOULD);
	 							}else{
		 							queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							queryMust2.add(descQuery,Occur.SHOULD);
		 							queryMust2.add(tagsQuery,Occur.SHOULD);
		 							queryMust2.add(contentQuery,Occur.SHOULD);
	 							}
	 							queryShould.add(queryMust2,Occur.MUST_NOT);
 							}
						}
						
					}
					queryMust2 = new BooleanQuery();
					String splitwords = splitKeywords2(keywords);
					String keywords2 = splitwords.trim();
//					if(keywords2.indexOf("/-")!=-1){
//						keywords2 = keywords2.replaceAll("/-", " ");
//					}
//					if(keywords2.indexOf("-")!=-1){
//						keywords2 = keywords2.replaceAll("-", " ");
//					}
//					if(keywords2.indexOf("/+")!=-1){
//						keywords2 = keywords2.replaceAll("/+", "+");
//					}
					while(true){
						keywords2 = keywords2.trim();
						if(keywords2.startsWith("+")){
							keywords2 = keywords2.substring(1);
						}else if(keywords2.endsWith("+")){
							keywords2 = keywords2.substring(0,keywords2.length()-1);
						}else{
							break;
						}
					}
					while(true){
						int num = keywords2.indexOf(" +");
						if(num!=-1){
							String str1 = keywords2.substring(0, num);
							String str2 = keywords2.substring(num+1);
							keywords2 = str1+"/"+str2;
						}else{
							break;
						}
					}
					//排除最前和最后为+号的情况
 					String []keys2 = keywords2.split("/+");// [ ]
					if(keys2.length>0){
						String []keys3 = keys2[0].split(" ");
						if(keys3!=null && keys3.length>0){
							//不管有没有[ ],都必须查到数据 &&（a ||b ||c）
							//要考虑分词的情况，改为or
							queryShould2 = new BooleanQuery();
							queryMust2 = new BooleanQuery();
							for(int i=0;i<keys3.length;i++){
								String key = keys3[i];
								if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
								key = key.trim().toLowerCase();
								if(StringUtils.isNotEmpty(key)){
									Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(fileNameQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 								if(keys2.length>1){
		 									queryMust2.add(tagsQuery,Occur.SHOULD);
		 									queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 							}else{
		 								if(keys2.length>1){
			 								queryMust2.add(fileNameQuery,Occur.SHOULD);
			 								queryMust2.add(descQuery,Occur.SHOULD);
			 								queryMust2.add(tagsQuery,Occur.SHOULD);
			 								queryMust3.add(queryMust2,Occur.SHOULD);
		 								}
		 								queryShould2.add(fileNameQuery,Occur.SHOULD);
		 								queryShould2.add(descQuery,Occur.SHOULD);
		 								queryShould2.add(tagsQuery,Occur.SHOULD);
		 							}
								}
							}
							queryShould.add(queryShould2,Occur.SHOULD);
							if(keys2.length>1){
								//存在加号时是必须的
								queryShould.add(queryMust3,Occur.MUST);
							}
						}
						
						if(keys2.length>1){//包含[/+]   //&& a && b && c
							for(int i=1;i<keys2.length;i++){
								queryMust2 = new BooleanQuery();
								String key = keys2[i];
	 							if(key.indexOf("/+")!=-1){
	 								key = key.replace("/+", "");
	 							}
	 							if(key.indexOf("+")!=-1){
	 								key = key.replace("+", "");
	 							}
	 							if(key.indexOf("/-")!=-1){
	 								key = key.replace("/-", "");
	 							}
	 							if(key.indexOf("-")!=-1){
	 								key = key.replace("-", "");
	 							}
	 							key = key.trim().toLowerCase();
	 							if(StringUtils.isNotEmpty(key)){
		 							Term fileNameTerm = new Term("fileName", "*"+key+"*");
		 							WildcardQuery fileNameQuery = new WildcardQuery(fileNameTerm);
		 							Term descTerm = new Term("description", "*"+key+"*");
		 							WildcardQuery descQuery = new WildcardQuery(descTerm);
		 							Term tagsTerm = new Term("tags", "*"+key+"*");
		 							WildcardQuery tagsQuery = new WildcardQuery(tagsTerm);

		 							Term contentTerm = new Term("content", "*"+key+"*");//全文检索
		 							WildcardQuery contentQuery = new WildcardQuery(contentTerm);
		 							
		 							fileNameQuery.setBoost(0.8f);//权重0.8 (最高)
	 								tagsQuery.setBoost(0.5f);//权重0.5
									descQuery.setBoost(0.3f);//权重0.3
									contentQuery.setBoost(0.1f);//权重0.1 (最低)
		 							if("name".equalsIgnoreCase(searchScope))//按名称搜索
		 							{
		 								queryMust2.add(fileNameQuery,Occur.SHOULD);
		 							}
		 							else if("tag".equalsIgnoreCase(searchScope))//按标签搜索
		 							{
		 								queryMust2.add(tagsQuery,Occur.SHOULD);
		 							}else{
			 							queryMust2.add(fileNameQuery,Occur.SHOULD);
			 							queryMust2.add(descQuery,Occur.SHOULD);
			 							queryMust2.add(tagsQuery,Occur.SHOULD);
			 							queryMust2.add(contentQuery,Occur.SHOULD);
		 							}
		 							queryShould.add(queryMust2,Occur.MUST);
	 							}
							}
						}
					}
				}
				
				query.add(queryShould,Occur.MUST);
				
				File file = new File(commLucenePath);				//获取lucene文件夹
				FSDirectory dir = FSDirectory.open(file);
				IndexReader iReader = null;
				if(IndexReader.indexExists(dir))
				{
					iReader = IndexReader.open(dir,true);//只读模式打开
				}
				else
				{
					return page;
				}
				
				BooleanQuery fileNameBQ = null;
				BooleanQuery suffixBQ = null;
				BooleanQuery addTimeBQ = null;
				BooleanQuery updateDateBQ = null;
				BooleanQuery sizeBQ = null;
				//添加搜索选项
				addTerm(qp,query,fileNameBQ,suffixBQ,addTimeBQ,updateDateBQ,sizeBQ,param);
				iSearcher = new IndexSearcher(iReader);		//创建索引查询器
				TopDocs topDocs = iSearcher.search(query,1000000000); 		//查询1000000000条记录，表示所有
				logger.debug("Lucene检索公共资料库文件,nowPage="+nowPage+",pageSize="+pageSize+",keywords="+keywords+" ,topDocs.hit"+topDocs.totalHits);
				String hight = "";
				if(!StringUtils.equals("false", param.get("highlightEnabled")))//启用关键字高亮度显示
				{
					hight = keywords;
				}
				page=pageService.initParameter(topDocs, iSearcher,"common", nowPage, pageSize,hight);
			}
			catch(Exception e)
			{
				logger.error("Lucene异常：",e);
			}
			finally
			{
				//关闭打开的对象，这里很重要，否则容易内存溢出
				try {
					if(null != dicReader) 
						dicReader.close();
					if(null != iSearcher)
						iSearcher.close();
					if(null != iSearcher1)
						iSearcher1.close();
					if(null != analyzer)
						analyzer.close();
					if(null != ikAnalyzer)
						ikAnalyzer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return page;
	}
	
	
	public static Page searchBizMailArchive(String keywords, int userId,
			PageService pageService, int nowPage, int pageSize,
			Map<String, String> param) {

		Page page = new Page();
		keywords = filterKeywords(keywords);
		String blurkey = getBlurkey(keywords);
		//
		IndexReader iReader = null;
		IndexSearcher iSearcher = null;
		IndexSearcher iSearcher1 = null;
		Analyzer analyzer = null;
		Analyzer ikAnalyzer = null;
		
		QueryParser qp=null;
		try {
			File file = new File(bizmailPath); // 获取lucene文件夹
			FSDirectory dir = FSDirectory.open(file);
			if (IndexReader.indexExists(dir)) {
				iReader = IndexReader.open(dir, true);// 只读模式打开
			} else {
				return page;
			}

			iSearcher = new IndexSearcher(iReader); // 创建索引查询器
			analyzer = new IKAnalyzer();// 使用IK分词器
			ikAnalyzer = new IKAnalyzer(true);// 使用IK智能分词器
			BooleanQuery queryShould = new BooleanQuery(); // 将条件进行组合

			String[] fields = null;
			String splitwords = splitKeywords2(keywords);
			String keywords2 = splitwords.trim();

			while (true) {
				keywords2 = keywords2.trim();
				if (keywords2.startsWith("+")) {
					keywords2 = keywords2.substring(1);
				} else if (keywords2.endsWith("+")) {
					keywords2 = keywords2.substring(0, keywords2.length() - 1);
				} else {
					break;
				}
			}
			while (true) {
				int num = keywords2.indexOf(" +");
				if (num != -1) {
					String str1 = keywords2.substring(0, num);
					String str2 = keywords2.substring(num + 1);
					keywords2 = str1 + "/" + str2;
				} else {
					break;
				}
			}
			
			
			if (param.get("selecttype").equals("0")) {
				
				BooleanQuery bq=new BooleanQuery();
				// 查询当前用户文件条件
				String uid = "_" + String.valueOf(userId) + "_";
				
				Term userIdTerm = new Term("viewid","*"+uid+"*"); 
				WildcardQuery  userIdQuery = new WildcardQuery(userIdTerm);
				userIdQuery.setBoost(0.7f);
				queryShould.add(userIdQuery,Occur.MUST);
				//标题
				Term titleTerm = new Term("title", "*" + keywords + "*");
				WildcardQuery titleQuery = new WildcardQuery(titleTerm);
				titleQuery.setBoost(0.6f);				
				bq.add(titleQuery, Occur.SHOULD);
				//正文内容
				Term content = new Term("content", "*" + keywords + "*");
				WildcardQuery contentQuery = new WildcardQuery(content);
				contentQuery.setBoost(0.5f);
				bq.add(contentQuery, Occur.SHOULD);
				//附件内容
				Term bizcontent = new Term("bizcontent", "*" + keywords + "*");
				WildcardQuery bizQuery = new WildcardQuery(bizcontent);
				bizQuery.setBoost(0.4f);
				bq.add(bizQuery, Occur.SHOULD);
				
				queryShould.add(bq, Occur.MUST);
				//模块类型
				Term module = new Term("module", param.get("s_type"));
				WildcardQuery moduleQuery = new WildcardQuery(module);	
				moduleQuery.setBoost(0.8f);
				queryShould.add(moduleQuery, Occur.MUST);
				fields = new String[] { "viewid", "title", "content","bizcontent","module","createTime"};	
				qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字
			} else if (param.get("selecttype").equals("1")) {
				String uid =  "_"+String.valueOf(userId)+"_";
				
				Term userIdTerm = new Term("viewid", "*"+uid+"*"); // 查询当前用户文件条件
				WildcardQuery userIdQuery = new WildcardQuery(userIdTerm);
				userIdQuery.setBoost(0.7f);		
				queryShould.add(userIdQuery,Occur.MUST);
				
				
				//标题
				Term titleTerm = new Term("title", "*" + keywords + "*");
				WildcardQuery titleQuery = new WildcardQuery(titleTerm);
				titleQuery.setBoost(0.6f);				
				queryShould.add(titleQuery, Occur.MUST);
				//模块类型
				Term module = new Term("module", param.get("s_type"));
				WildcardQuery moduleQuery = new WildcardQuery(module);	
				moduleQuery.setBoost(0.8f);
				queryShould.add(moduleQuery, Occur.MUST);
				fields = new String[] { "viewid", "title", "module","createTime"};
				qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字
			} else if (param.get("selecttype").equals("2")) {
				String uid =  "_"+String.valueOf(userId)+"_";
			
				Term userIdTerm = new Term("viewid", "*"+uid+"*"); // 查询当前用户文件条件
				WildcardQuery userIdQuery = new WildcardQuery(userIdTerm);
				userIdQuery.setBoost(0.7f);		
				queryShould.add(userIdQuery,Occur.MUST);
				//正文内容
				Term content = new Term("content", "*" + keywords + "*");
				WildcardQuery contentQuery = new WildcardQuery(content);
				contentQuery.setBoost(0.5f);
				queryShould.add(contentQuery, Occur.MUST);
				//模块类型
				Term module = new Term("module", param.get("s_type"));
				WildcardQuery moduleQuery = new WildcardQuery(module);	
				moduleQuery.setBoost(0.8f);
				queryShould.add(moduleQuery, Occur.MUST);
				fields = new String[] { "viewid", "content", "module","createTime"};
				qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字
			} else if (param.get("selecttype").equals("3")) {
				String uid =  "_"+String.valueOf(userId)+"_";
			
				Term userIdTerm = new Term("viewid", "*"+uid+"*"); // 查询当前用户文件条件
				WildcardQuery userIdQuery = new WildcardQuery(userIdTerm);
				userIdQuery.setBoost(0.7f);		
				queryShould.add(userIdQuery,Occur.MUST);
				//附件内容
				Term bizcontent = new Term("bizcontent", "*" + keywords + "*");
				WildcardQuery bizQuery = new WildcardQuery(bizcontent);
				bizQuery.setBoost(0.4f);
				queryShould.add(bizQuery, Occur.MUST);
				//模块类型
				Term module = new Term("module", param.get("s_type"));
				WildcardQuery moduleQuery = new WildcardQuery(module);	
				moduleQuery.setBoost(0.8f);
				queryShould.add(moduleQuery, Occur.MUST);
				fields = new String[] { "viewid", "bizcontent", "module","createTime"};
				qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字
			}
			BooleanQuery fileNameBQ = null;
			BooleanQuery suffixBQ = null;
			BooleanQuery addTimeBQ = null;
			BooleanQuery updateDateBQ = null;
			BooleanQuery sizeBQ = null;
			//添加搜索选项
			
			//qp = new LfsMultiFieldQueryParser(Version.LUCENE_34, fields, analyzer);	//多字段查询
			//qp.setDefaultOperator(QueryParser.AND_OPERATOR);//完整包含关键字
			addBizMaiTerm(qp,queryShould,fileNameBQ,suffixBQ,addTimeBQ,updateDateBQ,sizeBQ,param);
			TopDocs topDocs2 = iSearcher.search(queryShould, 1000000000);		
			 logger.debug("Lucene检索我的文件,nowPage="+nowPage+",pageSize="+pageSize+",keywords="+keywords+" ,topDocs.hit"+topDocs2.totalHits);
			String hight = "";
			if (!StringUtils.equals("false", param.get("highlightEnabled")))// 启用关键字高亮度显示
			{
				hight = keywords;
			}
			page = pageService.initParameter(topDocs2, iSearcher, "bizmail",
					nowPage, pageSize, hight);
		}

		catch (Exception e) {
			logger.error("Lucene异常：", e);
		} finally {
			// 关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if (null != iReader)
					iReader.close();
				if (null != iSearcher)
					iSearcher.close();
				if (null != iSearcher1)
					iSearcher1.close();
				if (null != analyzer)
					analyzer.close();
				if (null != ikAnalyzer)
					ikAnalyzer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return page;
	}
	
	
	/**
	 * 统计标签数
	 * @param tag
	 * @return
	 */
	public static int getTagCount(String tag) {
		int count = 0;
		if(StringUtils.isEmpty(tag))
		{
			return count;
		}
		Pattern p = Pattern.compile("\\s+");
		Matcher m = p.matcher(tag);
		if(m.find())
		{
			tag = m.replaceAll("");
		}
		if(StringUtils.isNotEmpty(tag))
		{
			IndexSearcher iSearcher = null;
			IndexReader iReader = null;
			IKAnalyzer ikAnalyzer = new IKAnalyzer();
			try
			{
				File file = new File(commLucenePath);
				FSDirectory dir = FSDirectory.open(file);
				if(IndexReader.indexExists(dir))
				{
					iReader = IndexReader.open(dir,true);//只读模式打开
				}
				else
				{
					return 0;
				}
				QueryParser parser = new QueryParser(Version.LUCENE_34, "tags", ikAnalyzer);
				Query query = parser.parse(tag);
				iSearcher = new IndexSearcher(iReader);
				TopDocs topDocs = iSearcher.search(query,1000000000);
				
				count = topDocs.totalHits;
			}
			catch(Exception e)
			{
				logger.error("Lucene异常：",e);
			}
			finally
			{
				//关闭打开的对象，这里很重要，否则容易内存溢出
				try {
					if(null != iSearcher)
						iSearcher.close();
					if(iReader != null)
						iReader.close();
					if(ikAnalyzer != null)
						ikAnalyzer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return count;
	}
	
	public static int[] getTagsCount(String[] tags) {
		int[] result = null;
		if(tags == null || tags.length == 0)
		{
			return result;
		}
		IndexSearcher iSearcher = null;
		IndexReader iReader = null;
		IKAnalyzer ikAnalyzer = new IKAnalyzer();
		try
		{
			File file = new File(commLucenePath);
			FSDirectory dir = FSDirectory.open(file);
			if(IndexReader.indexExists(dir))
			{
				iReader = IndexReader.open(dir,true);//只读模式打开
			}
			else
			{
				return result;
			}
			
			int length = tags.length;
			result = new int[length];
			Query query = null;
			TopDocs topDocs = null;
			iSearcher = new IndexSearcher(iReader);
			QueryParser parser = new QueryParser(Version.LUCENE_34, "tags", ikAnalyzer);
			for(int i=0;i<length;i++)
			{
				if(StringUtils.isNotEmpty(tags[i]))
				{
					query = parser.parse(tags[i]);
					query.setBoost(1.0f);
					topDocs = iSearcher.search(query,1000000000);
					result[i] = topDocs.totalHits;
				}
				else
				{
					result[i] = 0;
				}
			}
		}
		catch(Exception e)
		{
			logger.error("Lucene异常：",e);
		}
		finally
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != iSearcher)
					iSearcher.close();
				if(iReader != null)
					iReader.close();
				if(ikAnalyzer != null)
					ikAnalyzer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public synchronized static boolean createOrUpdateMyFileIndex(List<FileCache> fcList)
	{
		boolean flag =false;//成功标识
		Analyzer analyzer = null;
		try
		{
			analyzer = new IKAnalyzer();//使用IK分词器
			
			IndexWriterConfig myIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
			myIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
			File myFile = new File(myLucenePath);							//获取lucene文件夹
			
			FSDirectory dir = FSDirectory.open(myFile);
			if(null != myIwriter && IndexWriter.isLocked(dir)) {//先判断是否被锁
				myIwriter.close();
			}
			myIwriter = new IndexWriter(dir,myIwConfig); 	//创建索引器
			if(myIwriter == null)
			{
				logger.error("myfile lucene 初始化失败。");
				return false;
			}
			else if(null != fcList && fcList.size()>0)
			{
				FileCache fc = null;
				Document document = null;
				TermQuery idQuery = null;
				for(int idx=0;idx<fcList.size();idx++)
				{
					fc = fcList.get(idx);
					idQuery = new TermQuery(new Term("id",String.valueOf(fc.getId())));
					if(null != fc && fc.getStatus()>=0)//存在且正常
					{
						document = new Document();
						//t.id,t.pid,'common',t.path,t.name,t.tags,t.size,t.addTime,t.lastModified,t.userId,t.revision,t.revisionCount,t.file,t.description
						document.add(new Field("id", String.valueOf(fc.getId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//文件ID
						document.add(new Field("userId", String.valueOf(fc.getUserId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//用户ID
						document.add(new Field("fileName", fc.getName() , Field.Store.YES, Field.Index.ANALYZED));
						String ext = fc.getExt();
						if(StringUtils.isNotEmpty(ext))
						{
							document.add(new Field("ext", getExt(ext), Field.Store.YES, Field.Index.NOT_ANALYZED));
						}
						if(fc.getDescription()!=null&&!"".equals(fc.getDescription()))
						{
							document.add(new Field("description", fc.getDescription() , Field.Store.YES, Field.Index.ANALYZED));
						}
						if(fc.getPath()!=null&&!"".equals(fc.getPath()))
						{
							document.add(new Field("path", fc.getPath() , Field.Store.YES, Field.Index.NOT_ANALYZED));
						}
						if(fc.getTags()!=null&&!"".equals(fc.getTags()))
						{
							document.add(new Field("tags", fc.getTags() , Field.Store.YES, Field.Index.ANALYZED));
						}
						if(fc.getAddress()!=null&&!"".equals(fc.getAddress()))
						{
							document.add(new Field("address",fc.getAddress(), Field.Store.YES, Field.Index.ANALYZED));//地址位置标识
						}
						document.add(new Field("pId", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new NumericField("size", Field.Store.YES,true).setLongValue(fc.getSize()));
						document.add(new Field("revision", String.valueOf(fc.getRevision()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new Field("revisionCount", String.valueOf(fc.getRevisionCount()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new Field("isFile", String.valueOf(fc.isFile()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new NumericField("addTime", Field.Store.YES,true).setLongValue(fc.getAddTime().getTime()));
						document.add(new NumericField("lastModified", Field.Store.YES,true).setLongValue(fc.getLastModified()));
						//全文检索内容,20161215新增加，真正全文索引
						String fileKey =fc.getMd5()+fc.getSize();
						TikaFileContent fCountent = baseService.queryTikaFileContent(fileKey);
						if(null != fCountent && StringUtils.isNotEmpty(fCountent.getGunFileContent()))
						{
							document.add(new Field("content", fCountent.getGunFileContent(), Field.Store.YES, Field.Index.ANALYZED));
						}
//添加
						myIwriter.deleteDocuments(idQuery);		//先删除索引文档
						myIwriter.addDocument(document);	    //再添加索引文档
					}
					else
					{
						myIwriter.deleteDocuments(idQuery);		//先删除索引文档
					}
				}
				//操作完后，再同步和提交一下。减少IO
				myIwriter.optimize();
				myIwriter.commit();
				flag =true;
			}
		}catch(Exception e)
		{
			logger.error("Lucene异常：",e);
			flag =false;
		}
		finally
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != analyzer)
					analyzer.close();
				if(null != myIwriter)
				myIwriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	
	//删除个人文件单个文件索引
	public synchronized static boolean createOrUpdateMyFileIndexSingle(FileCache fc)
	{
		boolean flag =false;//成功标识
		Analyzer analyzer = null;
		try
		{
			analyzer = new IKAnalyzer();//使用IK分词器
			
			IndexWriterConfig myIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
			myIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
			File myFile = new File(myLucenePath);							//获取lucene文件夹
			
			FSDirectory dir = FSDirectory.open(myFile);
			if(null != myIwriter && IndexWriter.isLocked(dir)) {//先判断是否被锁
				myIwriter.close();
			}
			myIwriter = new IndexWriter(dir,myIwConfig); 	//创建索引器
			if(myIwriter == null)
			{
				logger.error("myfile lucene 初始化失败。");
				return false;
			}
			else if(null != fc)
			{
				Document document = null;
				TermQuery idQuery = null;
				idQuery = new TermQuery(new Term("id",String.valueOf(fc.getId())));
				if(null != fc && fc.getStatus()>=0)//存在且正常
				{
					document = new Document();
					//t.id,t.pid,'common',t.path,t.name,t.tags,t.size,t.addTime,t.lastModified,t.userId,t.revision,t.revisionCount,t.file,t.description
					document.add(new Field("id", String.valueOf(fc.getId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//文件ID
					document.add(new Field("userId", String.valueOf(fc.getUserId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//用户ID
					document.add(new Field("fileName", fc.getName() , Field.Store.YES, Field.Index.ANALYZED));
					String ext = fc.getExt();
					if(StringUtils.isNotEmpty(ext))
					{
						document.add(new Field("ext", getExt(ext), Field.Store.YES, Field.Index.NOT_ANALYZED));
					}
					if(fc.getDescription()!=null&&!"".equals(fc.getDescription()))
					{
						document.add(new Field("description", fc.getDescription() , Field.Store.YES, Field.Index.ANALYZED));
					}
					if(fc.getPath()!=null&&!"".equals(fc.getPath()))
					{
						document.add(new Field("path", fc.getPath() , Field.Store.YES, Field.Index.NOT_ANALYZED));
					}
					if(fc.getTags()!=null&&!"".equals(fc.getTags()))
					{
						document.add(new Field("tags", fc.getTags() , Field.Store.YES, Field.Index.ANALYZED));
					}
					if(fc.getAddress()!=null&&!"".equals(fc.getAddress()))
					{
						document.add(new Field("address",fc.getAddress(), Field.Store.YES, Field.Index.ANALYZED));//地址位置标识
					}
					document.add(new Field("pId", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new NumericField("size", Field.Store.YES,true).setLongValue(fc.getSize()));
					document.add(new Field("revision", String.valueOf(fc.getRevision()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new Field("revisionCount", String.valueOf(fc.getRevisionCount()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new Field("isFile", String.valueOf(fc.isFile()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new NumericField("addTime", Field.Store.YES,true).setLongValue(fc.getAddTime().getTime()));
					document.add(new NumericField("lastModified", Field.Store.YES,true).setLongValue(fc.getLastModified()));
					//全文检索内容,20161215新增加，真正全文索引
					String fileKey =fc.getMd5()+fc.getSize();
					TikaFileContent fCountent = baseService.queryTikaFileContent(fileKey);
					if(null != fCountent && StringUtils.isNotEmpty(fCountent.getGunFileContent()))
					{
						document.add(new Field("content", fCountent.getGunFileContent(), Field.Store.YES, Field.Index.ANALYZED));
					}
					//添加
					myIwriter.deleteDocuments(idQuery);		//先删除索引文档
					myIwriter.addDocument(document);	    //再添加索引文档
				}
				else
				{
					myIwriter.deleteDocuments(idQuery);		//先删除索引文档
				}
				//操作完后，再同步和提交一下。减少IO
				myIwriter.optimize();
				myIwriter.commit();
				flag =true;
			}
		}catch(Exception e)
		{
			logger.error("Lucene异常：",e);
			flag =false;//成功标识
		}
		finally
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != analyzer)
					analyzer.close();
				if(null != myIwriter)
				myIwriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	
	
	public synchronized static boolean createOrUpdateShareFileIndex(List<ShareFileCache> fcList , BaseService service)
	{
		boolean flag =false;//成功标识
		Analyzer analyzer = null;
		//sharefile lucene 		
		try
		{
			analyzer = new IKAnalyzer();//使用IK分词器
			IndexWriterConfig shareIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
			shareIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND); 
			File shareFile = new File(shareLucenePath);							//获取lucene文件夹
			
			FSDirectory dir = FSDirectory.open(shareFile);
			if(null != shareIwriter && IndexWriter.isLocked(dir)) {//先判断是否被锁
				shareIwriter.close();
			}
			shareIwriter = new IndexWriter(dir,shareIwConfig); 	//创建索引器
			if(shareIwriter == null)
			{
				logger.error("shareIwriter lucene 初始化失败。");
				return false;
			}
			else if(null != fcList && fcList.size()>0)
			{
				ShareFileCache fc = null;
				Document document = null;
				TermQuery idQuery = null;
				for(int idx=0;idx<fcList.size();idx++)
				{
					fc = fcList.get(idx);
					idQuery = new TermQuery(new Term("id",String.valueOf(fc.getId())));
					if(null != fc && fc.getStatus()>=0)//存在且正常
					{
						document = new Document();
						document.add(new Field("id", String.valueOf(fc.getId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//文件ID
						document.add(new Field("userId", String.valueOf(fc.getUserId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//用户ID
						document.add(new Field("fileName", fc.getName() , Field.Store.YES, Field.Index.ANALYZED));
						String ext = fc.getExt();
						if(StringUtils.isNotEmpty(ext))
						{
							document.add(new Field("ext", getExt(ext), Field.Store.YES, Field.Index.NOT_ANALYZED));
						}
						if(fc.getDescription()!=null&&!"".equals(fc.getDescription()))
						{
							document.add(new Field("description", fc.getDescription() , Field.Store.YES, Field.Index.ANALYZED));
						}
						if(fc.getPath()!=null&&!"".equals(fc.getPath()))
						{
							document.add(new Field("path", fc.getPath() , Field.Store.YES, Field.Index.NOT_ANALYZED));
						}
						if(fc.getTags()!=null&&!"".equals(fc.getTags()))
						{
							document.add(new Field("tags", fc.getTags() , Field.Store.YES, Field.Index.ANALYZED));
						}
//						if(fc.getAddress()!=null&&!"".equals(fc.getAddress()))
//						{
//							document.add(new Field("address",fc.getAddress(), Field.Store.YES, Field.Index.ANALYZED));//地址位置标识
//						}
						document.add(new Field("pId", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new NumericField("size", Field.Store.YES,true).setLongValue(fc.getSize()));
						document.add(new Field("revision", String.valueOf(fc.getRevision()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new Field("revisionCount", String.valueOf(fc.getRevisionCount()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new Field("receiveType", String.valueOf(fc.getReceiveType()) , Field.Store.YES, Field.Index.ANALYZED));
						document.add(new Field("isFile", String.valueOf(fc.isFile()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new NumericField("addTime", Field.Store.YES,true).setLongValue(fc.getAddTime().getTime()));
						document.add(new NumericField("lastModified", Field.Store.YES,true).setLongValue(fc.getLastModified()));
						//全文检索内容,20161215新增加，真正全文索引
						String fileKey =fc.getMd5()+fc.getSize();
						TikaFileContent fCountent = baseService.queryTikaFileContent(fileKey);
						if(null != fCountent && StringUtils.isNotEmpty(fCountent.getGunFileContent()))
						{
							document.add(new Field("content", fCountent.getGunFileContent(), Field.Store.YES, Field.Index.ANALYZED));
						}
						//接收的用户ids
						document.add(new Field("receiveUserIds",  fc.getReceiveUserIds(), Field.Store.YES, Field.Index.ANALYZED));
						//接收的部门ids
						document.add(new Field("receiveDepartmentId", fc.getReceiveDepartmentIds(), Field.Store.YES, Field.Index.ANALYZED));
						//接收的工作组ids
						document.add(new Field("receiveGroupId", fc.getReceiveGroupIds(), Field.Store.YES, Field.Index.ANALYZED));
						//添加
						shareIwriter.deleteDocuments(idQuery);		//先删除索引文档
						shareIwriter.addDocument(document);															//再添加索引文档
						
					}
					else
					{
						shareIwriter.deleteDocuments(idQuery);		//先删除索引文档
					}
				}
				//操作完后，再同步和提交一下。减少IO
				shareIwriter.optimize();
				shareIwriter.commit();
				flag =true;//成功标识
			}
		}catch(Exception e)
		{
			logger.error("Lucene异常：",e);
			flag =false;//成功标识
		}
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != analyzer)
					analyzer.close();
				if(null != shareIwriter)
					shareIwriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	/*
	public static void deleteMyFileIndex(long id)
	{
		try
		{
			myIwriter.deleteDocuments(new TermQuery(new Term("id",String.valueOf(id))));		//删除索引文档
		}
		catch(IOException e)
		{
			logger.debug("删除索引异常，可能索引已经不存在了");
		}
	}
	public static void deleteShareFileIndex(long id)
	{
		try
		{
			shareIwriter.deleteDocuments(new TermQuery(new Term("id",String.valueOf(id))));		//删除索引文档
		}
		catch(IOException e)
		{
			logger.debug("删除索引异常，可能索引已经不存在了");
		}
	}
	public static void deleteCommonFileIndex(long id)
	{
		try
		{
			commIwriter.deleteDocuments(new TermQuery(new Term("id",String.valueOf(id))));		//删除索引文档
		}
		catch(IOException e)
		{
			logger.debug("删除索引异常，可能索引已经不存在了");
		}
	}
	
	public static void createOrUpdateCommonFileIndex(Map<String,String> mfc)
	{
		CommonFileCache fc = new CommonFileCache();
		createOrUpdateCommonFileIndex(fc);
	}
	*/
	public synchronized static boolean createOrUpdateCommonFileIndex(List<CommonFileCache> fcList)
	{
		boolean flag =false;//成功标识
		Analyzer analyzer = null;
		try
		{
			analyzer = new IKAnalyzer();//使用IK分词器
			//采用内存的方式,经测试并未提高性能,暂不启用
//			IndexWriterConfig ramIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
//			ramIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND); 
//			ramDir = new RAMDirectory(); //内存路径         
//			ramIwriter = new IndexWriter(ramDir, ramIwConfig);
			
			IndexWriterConfig commIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
			commIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND); 
			File commFile = new File(commLucenePath);							//获取lucene文件夹
			
			FSDirectory dir = FSDirectory.open(commFile);
			if(null != commIwriter && IndexWriter.isLocked(dir)) {//先判断是否被锁
				commIwriter.close();
			}
			commIwriter = new IndexWriter(dir,commIwConfig); 	//创建索引器
			if(commIwriter == null)
			{
				logger.error("commIwriter lucene 初始化失败。");
				return false;
			}
			else if(null != fcList && fcList.size()>0)
			{
				CommonFileCache fc = null;
				Document document = null;
				TermQuery idQuery = null;
				for(int idx=0;idx<fcList.size();idx++)
				{
					fc = fcList.get(idx);
					idQuery = new TermQuery(new Term("id",String.valueOf(fc.getId())));
					if(null != fc && fc.getStatus()>=0)//存在且正常
					{
						logger.debug("更新索引 fileId:"+fc.getId()+",name:"+fc.getName());
						document = new Document();
						document.add(new Field("id", String.valueOf(fc.getId()) , Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS,TermVector.YES));		//文件ID
						document.add(new Field("userId", String.valueOf(fc.getUserId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//用户ID
						document.add(new Field("pid", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//父ID
						document.add(new Field("pid1", String.valueOf(fc.getPid1()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//一级节点ID
						document.add(new Field("pid2", String.valueOf(fc.getPid2()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//二级节点ID
						document.add(new Field("permissionsFileId", String.valueOf(fc.getPermissionsFileId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						if(fc.getName()!=null&&!"".equals(fc.getName()))
						{
							document.add(new Field("fileName", fc.getName() , Field.Store.YES, Field.Index.ANALYZED));
						}
						String ext = fc.getExt();
						if(StringUtils.isNotEmpty(ext))
						{
							document.add(new Field("ext", getExt(ext), Field.Store.YES, Field.Index.NOT_ANALYZED));
						}
						if(fc.getDescription()!=null&&!"".equals(fc.getDescription()))
						{
							document.add(new Field("description", fc.getDescription() , Field.Store.YES, Field.Index.ANALYZED));
						}
						if(fc.getTags()!=null&&!"".equals(fc.getTags()))
						{
							document.add(new Field("tags", fc.getTags() , Field.Store.YES, Field.Index.ANALYZED));
//							document.add(new Field("tag", fc.getTags() , Field.Store.YES, Field.Index.NOT_ANALYZED));//标签准确搜索时使用
						}
						if(fc.getAddress()!=null&&!"".equals(fc.getAddress()))
						{
							document.add(new Field("address",fc.getAddress(), Field.Store.YES, Field.Index.ANALYZED));//地址位置标识
						}
						if(StringUtils.isNotEmpty(fc.getPath()))
						{
							document.add(new Field("path", fc.getPath(), Field.Store.YES, Field.Index.ANALYZED));
						}
						document.add(new Field("pId", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new NumericField("size", Field.Store.YES,true).setLongValue(fc.getSize()));
						document.add(new Field("revision", String.valueOf(fc.getRevision()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new Field("revisionCount", String.valueOf(fc.getRevisionCount()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new Field("isFile", String.valueOf(fc.isFile()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
						document.add(new NumericField("addTime", Field.Store.YES,true).setLongValue(fc.getAddTime().getTime()));
						document.add(new NumericField("lastModified", Field.Store.YES,true).setLongValue(fc.getLastModified()));
						
						String attrs="";//用于直接将所有扩展属性合并到内容中（方便检查到）
						//文件属性值0-29
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue0()))
						{
							attrs += fc.getFileAttributeValue0() + " ";
							document.add(new Field("attr0", fc.getFileAttributeValue0(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue1()))
						{
							attrs += fc.getFileAttributeValue1() + " ";
							document.add(new Field("attr1", fc.getFileAttributeValue1(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue2()))
						{ 
							attrs += fc.getFileAttributeValue2() + " ";
							document.add(new Field("attr2", fc.getFileAttributeValue2(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue3()))
						{
							attrs += fc.getFileAttributeValue3() + " ";
							document.add(new Field("attr3", fc.getFileAttributeValue3(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue4()))
						{
							attrs += fc.getFileAttributeValue4() + " ";
							document.add(new Field("attr4", fc.getFileAttributeValue4(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue5()))
						{
							attrs += fc.getFileAttributeValue5() + " ";
							document.add(new Field("attr5", fc.getFileAttributeValue5(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue6()))
						{
							attrs += fc.getFileAttributeValue6() + " ";
							document.add(new Field("attr6", fc.getFileAttributeValue6(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue7()))
						{
							attrs += fc.getFileAttributeValue7() + " ";
							document.add(new Field("attr7", fc.getFileAttributeValue7(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue8()))
						{
							attrs += fc.getFileAttributeValue8() + " ";
							document.add(new Field("attr8", fc.getFileAttributeValue8(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue9()))
						{
							attrs += fc.getFileAttributeValue9() + " ";
							document.add(new Field("attr9", fc.getFileAttributeValue9(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue10()))
						{
							attrs += fc.getFileAttributeValue10() + " ";
							document.add(new Field("attr10", fc.getFileAttributeValue10(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue11()))
						{
							attrs += fc.getFileAttributeValue11() + " ";
							document.add(new Field("attr11", fc.getFileAttributeValue11(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue12()))
						{
							attrs += fc.getFileAttributeValue12() + " ";
							document.add(new Field("attr12", fc.getFileAttributeValue12(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue13()))
						{
							attrs += fc.getFileAttributeValue13() + " ";
							document.add(new Field("attr13", fc.getFileAttributeValue13(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue14()))
						{
							attrs += fc.getFileAttributeValue14() + " ";
							document.add(new Field("attr14", fc.getFileAttributeValue14(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue15()))
						{
							attrs += fc.getFileAttributeValue15() + " ";
							document.add(new Field("attr15", fc.getFileAttributeValue15(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue16()))
						{
							attrs += fc.getFileAttributeValue16() + " ";
							document.add(new Field("attr16", fc.getFileAttributeValue16(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue17()))
						{
							attrs += fc.getFileAttributeValue17() + " ";
							document.add(new Field("attr17", fc.getFileAttributeValue17(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue18()))
						{
							attrs += fc.getFileAttributeValue18() + " ";
							document.add(new Field("attr18", fc.getFileAttributeValue18(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue19()))
						{
							attrs += fc.getFileAttributeValue19() + " ";
							document.add(new Field("attr19", fc.getFileAttributeValue19(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue20()))
						{
							attrs += fc.getFileAttributeValue20() + " ";
							document.add(new Field("attr20", fc.getFileAttributeValue20(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue21()))
						{
							attrs += fc.getFileAttributeValue21() + " ";
							document.add(new Field("attr21", fc.getFileAttributeValue21(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue22()))
						{
							attrs += fc.getFileAttributeValue22() + " ";
							document.add(new Field("attr22", fc.getFileAttributeValue22(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue23()))
						{
							attrs += fc.getFileAttributeValue23() + " ";
							document.add(new Field("attr23", fc.getFileAttributeValue23(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue24()))
						{
							attrs += fc.getFileAttributeValue24() + " ";
							document.add(new Field("attr24", fc.getFileAttributeValue24(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue25()))
						{
							attrs += fc.getFileAttributeValue25() + " ";
							document.add(new Field("attr25", fc.getFileAttributeValue25(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue26()))
						{
							attrs += fc.getFileAttributeValue26() + " ";
							document.add(new Field("attr26", fc.getFileAttributeValue26(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue27()))
						{
							attrs += fc.getFileAttributeValue27() + " ";
							document.add(new Field("attr27", fc.getFileAttributeValue27(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue28()))
						{
							attrs += fc.getFileAttributeValue28() + " ";
							document.add(new Field("attr28", fc.getFileAttributeValue28(), Field.Store.YES, Field.Index.ANALYZED));
						}
						if(StringUtils.isNotEmpty(fc.getFileAttributeValue29()))
						{
							attrs += fc.getFileAttributeValue29() + " ";
							document.add(new Field("attr29", fc.getFileAttributeValue29(), Field.Store.YES, Field.Index.ANALYZED));
						}
						//全文检索内容,20161215新增加，真正全文索引
						String fileKey =fc.getMd5()+fc.getSize();
						TikaFileContent fCountent = baseService.queryTikaFileContent(fileKey);
						if(null != fCountent && StringUtils.isNotEmpty(fCountent.getGunFileContent()))
						{
							String content = fCountent.getGunFileContent();
							document.add(new Field("content", content, Field.Store.YES, Field.Index.ANALYZED));
							if(StringUtils.isNotEmpty(attrs))
							{
								//将自定义属性和内容 都添加到内容
								document.add(new Field("content", attrs +" "+content, Field.Store.YES, Field.Index.ANALYZED));
							}
						}
						else if(StringUtils.isNotEmpty(attrs))
						{
							//将自定义属性添加到内容
							document.add(new Field("content", attrs, Field.Store.YES, Field.Index.ANALYZED));
						}
						//添加
//						commIwriter.deleteDocuments(idQuery);		//先删除索引文档
//						commIwriter.addDocument(document);
						commIwriter.updateDocument(new Term("id",String.valueOf(fc.getId())),document);		//采用更新的方式,效率有很少量提升
						//采用内存的方式,经测试并未提高性能,暂不启用
//						ramIwriter.updateDocument(new Term("id",String.valueOf(fc.getId())),document);		//采用内存路径方式
						//再添加索引文档
					}
					else
					{
						logger.debug("删除索引 fileId:"+fc.getId()+",name:"+fc.getName());
						commIwriter.deleteDocuments(idQuery);		//先删除索引文档
					}
				}
				//操作完后，再同步和提交一下。减少IO
				/*
				 //采用内存的方式,经测试并未提高性能,暂不启用
				ramIwriter.commit();
				ramIwriter.close();
				commIwriter.addIndexes(new Directory[]{ramDir});*/
				
				commIwriter.optimize();
				commIwriter.commit();
				flag =true;//成功标识
			}
		}catch(Exception e)
		{
			logger.error("Lucene异常：",e);
			flag =false;//成功标识
		}
		finally
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != analyzer)
					analyzer.close();
				if(null != commIwriter)
				commIwriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	
	public synchronized static boolean createOrUpdateCommonFileSingle(CommonFileCache fc)
	{
		boolean flag =false;//成功标识
		Analyzer analyzer = null;
		try
		{
			analyzer = new IKAnalyzer();//使用IK分词器
			IndexWriterConfig commIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
			commIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND); 
			File commFile = new File(commLucenePath);							//获取lucene文件夹
			
			FSDirectory dir = FSDirectory.open(commFile);
			if(null != commIwriter && IndexWriter.isLocked(dir)) {//先判断是否被锁
				commIwriter.close();
			}
			commIwriter = new IndexWriter(dir,commIwConfig); 	//创建索引器
			if(commIwriter == null)
			{
				logger.error("commIwriter lucene 初始化失败。");
				return false;
			}
			else if(null != fc)
			{
				Document document = null;
				TermQuery idQuery = null;
				idQuery = new TermQuery(new Term("id",String.valueOf(fc.getId())));
				if(null != fc && fc.getStatus()>=0)//存在且正常
				{
					document = new Document();
					document.add(new Field("id", String.valueOf(fc.getId()) , Field.Store.YES,  Field.Index.NOT_ANALYZED_NO_NORMS,TermVector.YES));		//文件ID
					document.add(new Field("userId", String.valueOf(fc.getUserId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//用户ID
					document.add(new Field("pid", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//父ID
					document.add(new Field("pid1", String.valueOf(fc.getPid1()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//一级节点ID
					document.add(new Field("pid2", String.valueOf(fc.getPid2()) , Field.Store.YES, Field.Index.NOT_ANALYZED));		//二级节点ID
					document.add(new Field("permissionsFileId", String.valueOf(fc.getPermissionsFileId()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					if(fc.getName()!=null&&!"".equals(fc.getName()))
					{
						document.add(new Field("fileName", fc.getName() , Field.Store.YES, Field.Index.ANALYZED));
					}
					String ext = fc.getExt();
					if(StringUtils.isNotEmpty(ext))
					{
						document.add(new Field("ext", getExt(ext), Field.Store.YES, Field.Index.NOT_ANALYZED));
					}
					if(fc.getDescription()!=null&&!"".equals(fc.getDescription()))
					{
						document.add(new Field("description", fc.getDescription() , Field.Store.YES, Field.Index.ANALYZED));
					}
					if(fc.getTags()!=null&&!"".equals(fc.getTags()))
					{
						document.add(new Field("tags", fc.getTags() , Field.Store.YES, Field.Index.ANALYZED));
//							document.add(new Field("tag", fc.getTags() , Field.Store.YES, Field.Index.NOT_ANALYZED));//标签准确搜索时使用
					}
					if(fc.getAddress()!=null&&!"".equals(fc.getAddress()))
					{
						document.add(new Field("address",fc.getAddress(), Field.Store.YES, Field.Index.ANALYZED));//地址位置标识
					}
					if(StringUtils.isNotEmpty(fc.getPath()))
					{
						document.add(new Field("path", fc.getPath(), Field.Store.YES, Field.Index.ANALYZED));
					}
					document.add(new Field("pId", String.valueOf(fc.getPid()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new NumericField("size", Field.Store.YES,true).setLongValue(fc.getSize()));
					document.add(new Field("revision", String.valueOf(fc.getRevision()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new Field("revisionCount", String.valueOf(fc.getRevisionCount()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new Field("isFile", String.valueOf(fc.isFile()) , Field.Store.YES, Field.Index.NOT_ANALYZED));
					document.add(new NumericField("addTime", Field.Store.YES,true).setLongValue(fc.getAddTime().getTime()));
					document.add(new NumericField("lastModified", Field.Store.YES,true).setLongValue(fc.getLastModified()));

					String attrs="";//用于直接将所有扩展属性合并到内容中（方便检查到）
					//文件属性值0-29
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue0()))
					{
						attrs += fc.getFileAttributeValue0() + " ";
						document.add(new Field("attr0", fc.getFileAttributeValue0(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue1()))
					{
						attrs += fc.getFileAttributeValue1() + " ";
						document.add(new Field("attr1", fc.getFileAttributeValue1(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue2()))
					{
						attrs += fc.getFileAttributeValue2() + " ";
						document.add(new Field("attr2", fc.getFileAttributeValue2(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue3()))
					{
						attrs += fc.getFileAttributeValue3() + " ";
						document.add(new Field("attr3", fc.getFileAttributeValue3(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue4()))
					{
						attrs += fc.getFileAttributeValue4() + " ";
						document.add(new Field("attr4", fc.getFileAttributeValue4(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue5()))
					{
						attrs += fc.getFileAttributeValue5() + " ";
						document.add(new Field("attr5", fc.getFileAttributeValue5(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue6()))
					{
						attrs += fc.getFileAttributeValue6() + " ";
						document.add(new Field("attr6", fc.getFileAttributeValue6(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue7()))
					{
						attrs += fc.getFileAttributeValue7() + " ";
						document.add(new Field("attr7", fc.getFileAttributeValue7(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue8()))
					{
						attrs += fc.getFileAttributeValue8() + " ";
						document.add(new Field("attr8", fc.getFileAttributeValue8(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue9()))
					{
						attrs += fc.getFileAttributeValue9() + " ";
						document.add(new Field("attr9", fc.getFileAttributeValue9(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue10()))
					{
						attrs += fc.getFileAttributeValue10() + " ";
						document.add(new Field("attr10", fc.getFileAttributeValue10(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue11()))
					{
						attrs += fc.getFileAttributeValue11() + " ";
						document.add(new Field("attr11", fc.getFileAttributeValue11(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue12()))
					{
						attrs += fc.getFileAttributeValue12() + " ";
						document.add(new Field("attr12", fc.getFileAttributeValue12(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue13()))
					{
						attrs += fc.getFileAttributeValue13() + " ";
						document.add(new Field("attr13", fc.getFileAttributeValue13(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue14()))
					{
						attrs += fc.getFileAttributeValue14() + " ";
						document.add(new Field("attr14", fc.getFileAttributeValue14(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue15()))
					{
						attrs += fc.getFileAttributeValue15() + " ";
						document.add(new Field("attr15", fc.getFileAttributeValue15(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue16()))
					{
						attrs += fc.getFileAttributeValue16() + " ";
						document.add(new Field("attr16", fc.getFileAttributeValue16(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue17()))
					{
						attrs += fc.getFileAttributeValue17() + " ";
						document.add(new Field("attr17", fc.getFileAttributeValue17(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue18()))
					{
						attrs += fc.getFileAttributeValue18() + " ";
						document.add(new Field("attr18", fc.getFileAttributeValue18(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue19()))
					{
						attrs += fc.getFileAttributeValue19() + " ";
						document.add(new Field("attr19", fc.getFileAttributeValue19(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue20()))
					{
						attrs += fc.getFileAttributeValue20() + " ";
						document.add(new Field("attr20", fc.getFileAttributeValue20(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue21()))
					{
						attrs += fc.getFileAttributeValue21() + " ";
						document.add(new Field("attr21", fc.getFileAttributeValue21(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue22()))
					{
						attrs += fc.getFileAttributeValue22() + " ";
						document.add(new Field("attr22", fc.getFileAttributeValue22(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue23()))
					{
						attrs += fc.getFileAttributeValue23() + " ";
						document.add(new Field("attr23", fc.getFileAttributeValue23(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue24()))
					{
						attrs += fc.getFileAttributeValue24() + " ";
						document.add(new Field("attr24", fc.getFileAttributeValue24(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue25()))
					{
						attrs += fc.getFileAttributeValue25() + " ";
						document.add(new Field("attr25", fc.getFileAttributeValue25(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue26()))
					{
						attrs += fc.getFileAttributeValue26() + " ";
						document.add(new Field("attr26", fc.getFileAttributeValue26(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue27()))
					{
						attrs += fc.getFileAttributeValue27() + " ";
						document.add(new Field("attr27", fc.getFileAttributeValue27(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue28()))
					{
						attrs += fc.getFileAttributeValue28() + " ";
						document.add(new Field("attr28", fc.getFileAttributeValue28(), Field.Store.YES, Field.Index.ANALYZED));
					}
					if(StringUtils.isNotEmpty(fc.getFileAttributeValue29()))
					{
						attrs += fc.getFileAttributeValue29() + " ";
						document.add(new Field("attr29", fc.getFileAttributeValue29(), Field.Store.YES, Field.Index.ANALYZED));
					}
					//全文检索内容,20161215新增加，真正全文索引
					String fileKey =fc.getMd5()+fc.getSize();
					TikaFileContent fCountent = baseService.queryTikaFileContent(fileKey);
					if(null != fCountent && StringUtils.isNotEmpty(fCountent.getGunFileContent()))
					{
						document.add(new Field("content", fCountent.getGunFileContent(), Field.Store.YES, Field.Index.ANALYZED));
						if(StringUtils.isNotEmpty(attrs))
						{
							//将自定义属性和内容 都添加到内容
							document.add(new Field("content", attrs +" "+fCountent.getFileContent(), Field.Store.YES, Field.Index.ANALYZED));
						}
					}
					else if(StringUtils.isNotEmpty(attrs))
					{
						//将自定义属性添加到内容
						document.add(new Field("content", attrs, Field.Store.YES, Field.Index.ANALYZED));
					}
					
					//添加
//					commIwriter.deleteDocuments(idQuery);		//先删除索引文档
//					commIwriter.addDocument(document);													//再添加索引文档
					commIwriter.updateDocument(new Term("id",String.valueOf(fc.getId())),document);		//采用更新的方式,效率提高很少
				}
				else
				{
					commIwriter.deleteDocuments(idQuery);		//先删除索引文档
				}
				//操作完后，再同步和提交一下。减少IO
				commIwriter.optimize();
				commIwriter.commit();
				flag =true;//成功标识
			}
		}catch(Exception e)
		{
			logger.error("Lucene异常：",e);
			flag =false;//成功标识
		}
		finally
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != analyzer)
					analyzer.close();
				if(null != commIwriter)
				commIwriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	
	
	

	public synchronized static boolean createOrUpdateBizMailIndex(List<BizMailArchive> fcList)
	{
		boolean flag =false;//成功标识
		Analyzer analyzer = null;
		try
		{
			analyzer = new IKAnalyzer();//使用IK分词器
			
			IndexWriterConfig myIwConfig = new IndexWriterConfig(Version.LUCENE_34 , analyzer); 	//索引配置
			myIwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
			File myFile = new File(bizmailPath);							//获取lucene文件夹
			
			FSDirectory dir = FSDirectory.open(myFile);
			if(null != myIwriter && IndexWriter.isLocked(dir)) {//先判断是否被锁
				myIwriter.close();
			}
			myIwriter = new IndexWriter(dir,myIwConfig); 	//创建索引器
			if(myIwriter == null)
			{
				logger.error("myfile lucene 初始化失败。");
				return false;
			}
			else if(null != fcList && fcList.size()>0)
			{
				BizMailArchive fc = null;
				Document document = null;
				TermQuery idQuery = null;
				for(int idx=0;idx<fcList.size();idx++)
				{
					StringBuffer sb=new StringBuffer();
					String id="";
					fc = fcList.get(idx);
					idQuery = new TermQuery(new Term("id",String.valueOf(fc.getId())));
					if(null != fc)//存在且正常
					{
						document = new Document();						
						//viewid
							StringBuffer ids=new StringBuffer();
							ids.append("_");				 
							//获取viewer
							if(StringUtils.isNotEmpty(fc.getViewer())){																	
							JsonObject jo = new JsonParser().parse(fc.getViewer()).getAsJsonObject();  
							if(null!=jo && !"".equals(jo)){
								//组id
								String group=jo.get("group").toString();
								if(null!=group && !"".equals(group)){
									if(group.contains(",")){
										group=group.replaceAll(",","','").replaceAll("\"", "'");
									}else{
										group=group.replaceAll("\"", "'");
									}
									//群组 1 
									List<Group> groups=baseService.query("FROM Group t where t.name in "+"("+group+")");
									if(groups!=null && groups.size()>0){
										for (Group group2 : groups) {
											if(null!=group2 ){
												long groudid=group2.getId();
												List<User> users=baseService.query("SELECT u FROM Group t INNER JOIN t.groupUsers u WHERE t.id="+groudid);
												if(users!=null && users.size()>0){
													for (User user : users) {														
														ids.append(user.getId()).append("_");											
													}
												}																							
											}	
										}
									}
									
								}	
								//部门
								String dept=jo.get("dept").toString();
								if(null!=dept && !"".equals(dept)){
									if(dept.contains(",")){
										dept=dept.replaceAll(",","','").replaceAll("\"", "'");
									}else{
										dept=dept.replaceAll("\"", "'");
									}
									//群组 1 
									List<Department> depts=baseService.query("FROM Department t where t.name in "+"("+dept+")");
									if(depts!=null && depts.size()>0){
										for (Department dep : depts) {
											if(null!=dep ){
												long depid=dep.getId();
												List<User> users=baseService.query("FROM User u where u.departmentId="+depid);
												if(users!=null && users.size()>0){
													for (User user : users) {														
														ids.append(user.getId()).append("_");											
													}
												}																							
											}	
										}
									}
									
								}
								
								//用户
								String user=jo.get("user").toString();
								if(null!=user && !"".equals(user)){
									if(user.contains(",")){
										user=user.replaceAll(",","','").replaceAll("\"", "'");
									}else{
										user=user.replaceAll("\"", "'");
									}
									//群组 1 
									List<User> users=baseService.query("FROM User t where t.username in "+"("+user+")");
									if(users!=null && users.size()>0){
										for (User usr : users) {
											if(null!=usr ){
												int usrid=usr.getId();																												
												ids.append(usrid).append("_");																																													
											}	
										}
									}
									
								}
								//超级管理员
								List<User> users=baseService.query("FROM User t where t.role.type=0");
								if(users!=null && users.size()>0)
								for (User user2 : users) {
									ids.append(user2.getId()).append("_");		
								}
								
							}
							
						}else{
							//超级管理员
							List<User> users=baseService.query("FROM User t where t.role.type=0");
							if(users!=null && users.size()>0)
							for (User user2 : users) {
								ids.append(user2.getId()).append("_");
							}
							
						}							
						String str = ids.toString();
						document.add(new Field("viewid", str, Field.Store.YES, Field.Index.NOT_ANALYZED));
						
						//id
						if(fc.getId()!=null&&fc.getId()>0)
						{
							document.add(new Field("id", String.valueOf(fc.getId()), Field.Store.YES, Field.Index.ANALYZED));
						}
						//類型
						if(fc.getModuleName()!=null&&!"".equals(fc.getModuleName()))
						{
							BizMailModule b=(BizMailModule) baseService.queryObject("FROM BizMailModule t where t.name="+"'"+fc.getModuleName()+"'");
							document.add(new Field("module", String.valueOf(b.getId()), Field.Store.YES, Field.Index.ANALYZED));
						}
						
						//标题 关键字搜索
						if(fc.getTitle()!=null&&!"".equals(fc.getTitle()))
						{
							document.add(new Field("title", fc.getTitle() , Field.Store.YES, Field.Index.ANALYZED));
						}
						//摘要
						if(fc.getSummary()!=null&&!"".equals(fc.getSummary())){
							document.add(new Field("summary", fc.getSummary() , Field.Store.YES, Field.Index.ANALYZED));
						}
						
						//文档链接地址
						if(fc.getDocLinkurl()!=null&&!"".equals(fc.getDocLinkurl())){
							document.add(new Field("docLinkurl", fc.getDocLinkurl() , Field.Store.YES, Field.Index.ANALYZED));
						}
						
						//创建人
						if(fc.getCreateUser()!=null&&!"".equals(fc.getCreateUser())){
							document.add(new Field("createUser", fc.getCreateUser() , Field.Store.YES, Field.Index.ANALYZED));
						}
						//创建部门
						if(fc.getCreateDepartment()!=null&&!"".equals(fc.getCreateDepartment())){
							document.add(new Field("createDepartment", fc.getCreateDepartment() , Field.Store.YES, Field.Index.ANALYZED));
						}
						
						//时间
						document.add(new NumericField("createTime", Field.Store.YES,true).setLongValue(fc.getCreateTime().getTime()));
						BizMailContent bmc=baseService.queryByPK(BizMailContent.class, fc.getId());						
						//正文搜索
						if(null!=bmc && bmc.getContent()!=null&&!"".equals( bmc.getContent()))
						{
							document.add(new Field("content", bmc.getContent() , Field.Store.NO, Field.Index.ANALYZED));
						}
						//附件内容搜索
						List<Annex> an=baseService.query("FROM Annex t where t.bizId="+"'"+fc.getBizid()+"'");
						for (int i = 0; i <an.size(); i++) {
							CommonFileCache cfclist=baseService.queryByPK(CommonFileCache.class, an.get(i).getDataId());
							String fileKey =cfclist.getMd5()+cfclist.getSize();
							TikaFileContent fCountent = baseService.queryTikaFileContent(fileKey);
							if(null != fCountent && StringUtils.isNotEmpty(fCountent.getGunFileContent()))
							{
								String content = fCountent.getGunFileContent();
								sb.append(content);
							}
							
						}
						document.add(new Field("bizcontent", sb.toString(), Field.Store.NO, Field.Index.ANALYZED));
						myIwriter.deleteDocuments(idQuery);		//先删除索引文档
						myIwriter.addDocument(document);	    //再添加索引文档
					
				}
					//操作完后，再同步和提交一下。减少IO
					myIwriter.optimize();
					myIwriter.commit();
					flag =true;
				}
			}
		
		}catch(Exception e){
			logger.error("Lucene异常：",e);
			flag =false;
		}
		finally
		{
			//关闭打开的对象，这里很重要，否则容易内存溢出
			try {
				if(null != analyzer)
					analyzer.close();
				if(null != myIwriter)
				myIwriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return flag;
	}
	
	
	
	/**
	 * 添加搜索选项
	 * 
	 * @param qp
	 * @param bq
	 * @param fileNameBQ
	 * @param addTimeBQ
	 * @param suffixBQ
	 * @param updateDateBQ
	 * @param sizeBQ
	 * @param param
	 * @throws ParseException
	 * @throws java.text.ParseException
	 */
	public static void addTerm(QueryParser qp,BooleanQuery bq,BooleanQuery fileNameBQ,BooleanQuery suffixBQ,
							   BooleanQuery addTimeBQ,BooleanQuery updateDateBQ,BooleanQuery sizeBQ,
							   Map<String,String> param) throws ParseException, java.text.ParseException {
		int fileSize = 0;
		if(null != param) {
			if(StringUtils.isNotEmpty(param.get("filename"))) {
				//在fileName域中查询包含filename的document
				Term fileNameTerm = new Term("fileName", param.get("filename"));				
				FuzzyQuery fileNameQuery = new FuzzyQuery(fileNameTerm); 
				fileNameBQ = new BooleanQuery(); 				
				fileNameBQ.add(fileNameQuery,Occur.MUST);
				bq.add(fileNameBQ,Occur.MUST);
			}
			if(StringUtils.isNotEmpty(param.get("suffix"))) {
				//Term suffixTerm = new Term("fileName", "?*." + param.get("suffix"));
				Term suffixTerm = new Term("ext", param.get("suffix"));
				Query suffixQuery = new WildcardQuery(suffixTerm);
				suffixBQ = new BooleanQuery(); 				
				suffixBQ.add(suffixQuery,Occur.MUST);
				bq.add(suffixBQ,Occur.MUST);
			}
			//文件大小
			fileSize = Integer.valueOf(param.get("fileSize"));
			if(fileSize != 0) {
				long sizeFrom = 0;
				long sizeTo = 1;
				if(fileSize == 1) {//小于100KB
					sizeTo = 100 * 1024 - 1;
				}else if(fileSize == 2) {//小于1MB
					sizeTo = 1024 * 1024 - 1;
				}else if(fileSize == 3) {//大于 等于 1MB
					sizeFrom = 1024 * 1024;
					sizeTo = sizeFrom * 1024 * 5;//5GB
				}
				sizeBQ = new BooleanQuery(); 				
				Query sizeQuery = qp.parse("size:[" + sizeFrom + " TO "+ sizeTo + "]");  
				sizeBQ.add(sizeQuery,Occur.MUST);
				bq.add(sizeBQ,Occur.MUST);
			}
			if(StringUtils.isNotEmpty(param.get("tag"))) {
				Term tagTerm = new Term("tags", param.get("tag"));				
				FuzzyQuery tagQuery = new FuzzyQuery(tagTerm); 
				BooleanQuery tagBQ = new BooleanQuery(); 				
				tagBQ.add(tagQuery,Occur.MUST);
				bq.add(tagBQ,Occur.MUST);
			}
			//创建时间
			if(StringUtils.isNotEmpty(param.get("createDateFrom")) && StringUtils.isNotEmpty(param.get("createDateTo"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				addTimeBQ = new BooleanQuery();
				long dateFrom = f.parse(param.get("createDateFrom") + " 00:00:00").getTime();
				long dateTo = f.parse(param.get("createDateTo") + " 23:59:59").getTime();
				Query addTimeQuery = qp.parse("addTime:[" + dateFrom + " TO " + dateTo + "]");  
				addTimeBQ.add(addTimeQuery,Occur.MUST);
				bq.add(addTimeBQ,Occur.MUST);
			}else if(StringUtils.isNotEmpty(param.get("createDateFrom")) && StringUtils.isEmpty(param.get("createDateTo"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				addTimeBQ = new BooleanQuery();
				long dateFrom = f.parse(param.get("createDateFrom") + " 00:00:00").getTime();
				long dateTo = new Date().getTime();
				Query addTimeQuery = qp.parse("addTime:[" + dateFrom + " TO " + dateTo + "]");  
				addTimeBQ.add(addTimeQuery,Occur.MUST);
				bq.add(addTimeBQ,Occur.MUST);
			}else if(StringUtils.isEmpty(param.get("createDateFrom")) && StringUtils.isNotEmpty(param.get("createDateTo"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				addTimeBQ = new BooleanQuery();
				long dateFrom = f.parse("1970-01-01 00:00:00").getTime();
				long dateTo = f.parse(param.get("createDateTo") + " 23:59:59").getTime();
				Query addTimeQuery = qp.parse("addTime:[" + dateFrom + " TO " + dateTo + "]");  
				addTimeBQ.add(addTimeQuery,Occur.MUST);
				bq.add(addTimeBQ,Occur.MUST);
			}
			//更新时间
			if(StringUtils.isNotEmpty(param.get("updateDateFrom")) && StringUtils.isNotEmpty(param.get("updateDateTo"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				updateDateBQ = new BooleanQuery(); 				
				long dateFrom = f.parse(param.get("updateDateFrom") + " 00:00:00").getTime();
				long dateTo = f.parse(param.get("updateDateTo") + " 23:59:59").getTime();
				Query updateDateQuery = qp.parse("lastModified:[" + dateFrom + " TO " + dateTo + "]"); 
				updateDateBQ.add(updateDateQuery,Occur.MUST);
				bq.add(updateDateBQ,Occur.MUST);
			}else if(StringUtils.isNotEmpty(param.get("updateDateFrom")) && StringUtils.isEmpty(param.get("updateDateTo"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				updateDateBQ = new BooleanQuery(); 				
				long dateFrom = f.parse(param.get("updateDateFrom") + " 00:00:00").getTime();
				long dateTo = new Date().getTime();
				Query updateDateQuery = qp.parse("lastModified:[" + dateFrom + " TO " + dateTo + "]"); 
				updateDateBQ.add(updateDateQuery,Occur.MUST);
				bq.add(updateDateBQ,Occur.MUST);
			}else if(StringUtils.isEmpty(param.get("updateDateFrom")) && StringUtils.isNotEmpty(param.get("updateDateTo"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				updateDateBQ = new BooleanQuery(); 				
				long dateFrom = f.parse("1970-01-01 00:00:00").getTime();
				long dateTo = f.parse(param.get("updateDateTo") + " 23:59:59").getTime();
				Query updateDateQuery = qp.parse("lastModified:[" + dateFrom + " TO " + dateTo + "]"); 
				updateDateBQ.add(updateDateQuery,Occur.MUST);
				bq.add(updateDateBQ,Occur.MUST);
			}
		}
	}
	
	
	public static void addBizMaiTerm(QueryParser qp,BooleanQuery bq,BooleanQuery fileNameBQ,BooleanQuery suffixBQ,
			BooleanQuery addTimeBQ,BooleanQuery updateDateBQ,BooleanQuery sizeBQ,
			Map<String,String> param) throws ParseException, java.text.ParseException {
		if(null != param) {	

			
				
			//创建时间
			if(StringUtils.isNotEmpty(param.get("search_begintime")) && StringUtils.isEmpty(param.get("search_endtime")) ) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				long dateFrom = f.parse(param.get("search_begintime") + " 00:00:00").getTime();
				long dateTo = new Date().getTime();
				updateDateBQ = new BooleanQuery(); 
				//Query updateDateQuery = qp.parse("createTime:[" + dateFrom + " TO " + dateTo + "]"); 
		        //TermRangeQuery rangeQuery = new TermRangeQuery("createTime", String.valueOf(dateFrom), String.valueOf(dateTo), true, true);
		        Query updateDateQuery =NumericRangeQuery.newLongRange("createTime", dateFrom, dateTo, true,true);  
		        updateDateBQ.add(updateDateQuery,Occur.MUST);

				bq.add(updateDateBQ,Occur.MUST);
			}else if(StringUtils.isNotEmpty(param.get("search_begintime")) && StringUtils.isNotEmpty(param.get("search_endtime"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				updateDateBQ = new BooleanQuery(); 
				long dateFrom = f.parse(param.get("search_begintime") + " 00:00:00").getTime();
				long dateTo = f.parse(param.get("search_endtime") + " 23:59:59").getTime();
				// TermRangeQuery rangeQuery = new TermRangeQuery("createTime", String.valueOf(dateFrom), String.valueOf(dateTo), true, true);
				 Query updateDateQuery =NumericRangeQuery.newLongRange("createTime", dateFrom, dateTo, true,true);
				updateDateBQ.add(updateDateQuery,Occur.MUST);

				bq.add(updateDateBQ,Occur.MUST);
			}else if(StringUtils.isEmpty(param.get("search_begintime")) && StringUtils.isNotEmpty(param.get("search_endtime"))) {
				SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				updateDateBQ = new BooleanQuery(); 
				long dateFrom = f.parse("1970-01-01 00:00:00").getTime();
				long dateTo = f.parse(param.get("search_endtime") + " 23:59:59").getTime();
				//TermRangeQuery rangeQuery = new TermRangeQuery("createTime", String.valueOf(dateFrom), String.valueOf(dateTo), true, true);
				 Query updateDateQuery =NumericRangeQuery.newLongRange("createTime", dateFrom, dateTo, true,true);
				updateDateBQ.add(updateDateQuery,Occur.MUST);
				bq.add(updateDateBQ,Occur.MUST);
			}
			
		}
	}
	
	
	
	

	public static String getExt(String ext) {
		if(StringUtils.isNotEmpty(ext))
		{
			ext = ext.toLowerCase();
			if(StringUtils.equals(ext, "docx") || StringUtils.equals(ext, "pptx") || StringUtils.equals(ext, "xlsx"))
			{
				ext = ext.substring(0,ext.length()-1);
			}
		}
		return ext;
	}
	
}
