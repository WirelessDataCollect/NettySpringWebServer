<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>  <%-- 自定义标签--%>
<%@page language="java" import="java.util.*" pageEncoding="UTF-8" %> <!-- 设置页面的属性 -->
<html>
<head>
    <title>Login</title>
    <%
    String IP = request.getRemoteAddr();
    String Info = request.getHeader("user-agent");
    %>
</head>
<body>

<h2>Login</h2>
<form:form method="POST" action="/NettySpringWebServer/working">
   <table>
    <tr>
        <td><form:label path="name">Name</form:label></td>
        <td><form:input path="name" /></td>
    </tr>
    <tr>
        <td><form:label path="key">key</form:label></td>
        <td><form:input path="key" /></td>
    </tr>
    <tr>
        <td colspan="2">
            <input type="submit" value="Submit"/>
        </td>
    </tr>
	<tr></tr><tr></tr><tr></tr>
     <tr>
        <td colspan="2">   
		<p>IP: <%=IP %> <br>
		Info：<%=Info %></p>
        </td>
    </tr>
     <tr>
        <td colspan="2">   
		<%=new Date()%> <%-- 显示当前时间 --%>
        </td>
    </tr>

</table>  
<div id="beian" style='position:absolute;bottom:0px;text-align:center'>Copyright © 2013-2018 NeSC课题组测试  nesctech.com All Rights Reserved. 备案号：浙ICP备<a href="http://www.miitbeian.gov.cn">18047772</a>号</div>
    

</form:form>
</body>
</html>
