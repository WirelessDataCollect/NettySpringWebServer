<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>  <%-- 自定义标签--%>
<%@page language="java" import="java.util.*" pageEncoding="UTF-8" %> <!-- 设置页面的属性 -->
<html>
<head>
    <title>Spring MVC Form Handling</title>
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
</form:form>
</body>
</html>
