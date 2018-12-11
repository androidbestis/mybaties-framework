/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  private final MapperBuilderAssistant builderAssistant;
  private final XNode context;
  private final String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }

  /**
   * 解析mapper xml文件里面的:select,insert,update,delete节点
   */
  public void parseStatementNode() {
    //获取id属性值
    String id = context.getStringAttribute("id");
    //获取databaseId选择执行sql的数据库<多数据源支持>
    String databaseId = context.getStringAttribute("databaseId");

    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }
    //获取默认取出fetchSize条数据的值
    //设置一个值后，驱动器会在结果集数目达到此数值后，激发返回，默认为不设值，由驱动器自己决定
    Integer fetchSize = context.getIntAttribute("fetchSize");
    //获取sql超时时间
    //设置驱动器在抛出异常前等待回应的最长时间，默认为不设值，由驱动器自己决定
    Integer timeout = context.getIntAttribute("timeout");
    //获取parameterMap值
    String parameterMap = context.getStringAttribute("parameterMap");
    //获取parameterType值   传给此语句的参数的完整类名或别名
    String parameterType = context.getStringAttribute("parameterType");
    //通过反射获取：parameterType="com.atguigu.mybatis.bean.Employee" 的Class类型
    Class<?> parameterTypeClass = resolveClass(parameterType);
    //获取resultMap值
    //引用的外部resultMap 名。结果集映射是MyBatis 中最强大的特性。许多复杂的映射都可以轻松解决。（resultType 与resultMap 不能并用）
    String resultMap = context.getStringAttribute("resultMap");
    //获取resultType值
    //语句返回值类型的整类名或别名。注意，如果是集合，那么这里填写的是集合的项的整类名或别名，而不是集合本身的类名。（resultType 与resultMap 不能并用）
    String resultType = context.getStringAttribute("resultType");
    //获取针对特殊的语句指定特定语言
    String lang = context.getStringAttribute("lang");

    LanguageDriver langDriver = getLanguageDriver(lang);
    //通过反射获取：resultType="Blog"的Class类型
    Class<?> resultTypeClass = resolveClass(resultType);
    //FORWARD_ONLY|SCROLL_SENSITIVE|SCROLL_INSENSITIVE中的一种。默认是不设置（驱动自行处理）。
    //forward_only，scroll_sensitive，scroll_insensitive
    //只转发，滚动敏感，不区分大小写的滚动
    //驱动器决定
    String resultSetType = context.getStringAttribute("resultSetType");
    //STATEMENT,PREPARED或CALLABLE的一种。这会让MyBatis使用选择使用Statement，PreparedStatement或CallableStatement。默认值：PREPARED。
    //statement，preparedstatement，callablestatement。
    //语句、预准备语句、可调用语句
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));

    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    String nodeName = context.getNode().getNodeName();
    //获取SQl语句类型:  UNKNOWN, INSERT, UPDATE, DELETE, SELECT, FLUSH;
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    //是否是查询语句
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    //将其设置为true，不论语句什么时候被带掉用，都会导致缓存被清空。默认值：false。
    //如果设为true，则会在每次语句调用的时候就会清空缓存。select 语句默认设为false
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    //将其设置为true，将会导致本条语句的结果被缓存。默认值：true。
    //如果设为true，则语句的结果集将被缓存。select 语句默认设为false
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    //这个设置仅针对嵌套结果 select 语句适用：如果为 true，就是假设包含了嵌套结果集或是分组了，这样的话当返回一个主结果行的时候，就不会发生有对前面结果集的引用的情况。
    // 这就使得在获取嵌套的结果集的时候不至于导致内存不够用。默认值：false。
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    //解析<include refid="query_user_where"></include>代码片段
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // Parse selectKey after includes and remove them.
    //mybatis可以将insert的数据的主键返回，直接拿到新增数据的主键，以便后续使用。
    //这里主要说的是selectKey标签
    //设计表的时候有两种主键，一种自增主键，一般为int类型，一种为非自增的主键，例如用uuid等。
    //解析selectKey
    // <selectKey keyColumn="myNo" keyProperty="id" order="AFTER" resultType="java.lang.Integer" statementType="PREPARED">
    //    SELECT LAST_INSERT_ID()
    //</selectKey>
    //selectKey 标签表示: 子查询中主键的提取问题
    processSelectKeyNodes(id, parameterTypeClass, langDriver);
    
    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    String resultSets = context.getStringAttribute("resultSets");
    String keyProperty = context.getStringAttribute("keyProperty");
    String keyColumn = context.getStringAttribute("keyColumn");
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    //将MappedStatement（解析过的Sql添加到Configuration中）
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered, 
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    //获取selectKey节点
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    if (configuration.getDatabaseId() != null) {
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    removeSelectKeyNodes(selectKeyNodes);
  }

  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * 解析selectKey节点信息
   * <selectKey keyColumn="myNo" keyProperty="id" order="AFTER" resultType="java.lang.Integer" statementType="PREPARED">
        SELECT LAST_INSERT_ID()
     </selectKey>
   */
  //selectKey 标签表示: 子查询中主键的提取问题
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    //获取resultType属性值  resultType="int"表示返回值得类型为int类型
    String resultType = nodeToHandle.getStringAttribute("resultType");
    //通过反射获取resultType值的Class类类型
    Class<?> resultTypeClass = resolveClass(resultType);
    //获取statementType属性值: Mybaties支持三种statement : statement,preparedstatement,callablestatement
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    //获取keyProperty的属性值   keyProperty表示将属性设置到某个列中
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    //keyColumn表示查询语句返回结果的列名
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    //order等于BEFORE    order="BEFORE 表示在插入语句之前执行,默认是AFTER
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    //defaults
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    //创建SqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    //Select语句类型
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    //
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    id = builderAssistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this statement if there is a previous one with a not null databaseId
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (this.configuration.hasStatement(id, false)) {
        MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
        if (previous.getDatabaseId() != null) {
          return false;
        }
      }
    }
    return true;
  }

  private LanguageDriver getLanguageDriver(String lang) {
    Class<?> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    return builderAssistant.getLanguageDriver(langClass);
  }

}
