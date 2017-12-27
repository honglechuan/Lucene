<%@ page language="java" import="java.util.*" pageEncoding="utf-8"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <head>
    <base href="<%=basePath%>">
    
    <title>My JSP 'Test20171016.jsp' starting page</title>
    
	<meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
	<meta http-equiv="description" content="This is my page">
	<!--
	<link rel="stylesheet" type="text/css" href="styles.css">
	-->
<script type="text/javascript">
function getData( ) {
    $.ajax({
        var d1=$("#d11").val();
        url : "test/t1.do",
        type : "GET",
        async:false,
        beforeSend : function(request) {
          
        },
        data : {
                 dtp1:d1,
         },
        dataType:'jsonp', 
        jsonp:'callback',      
        crossDomain:true,/*默认是false，记住要改为true，不然无法成功获取数据数据并跳转至success*/
        jsonpCallback:"callback",
        success:function(data){
            alert("success");
        },
        error:function(XMLHttpRequest, textStatus, errorThrown) {
            alert(XMLHttpRequest.status);
            alert(XMLHttpRequest.readyState);
            alert(textStatus);
        }
   });

</script>
  </head>
  
  <body>
   <br>
   <input type="button" onclick="getData()" value="跨域测试"/>
  </body>
</html>
