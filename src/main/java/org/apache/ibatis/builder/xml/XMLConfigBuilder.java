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

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  //解析的标志
  private boolean parsed;
  //xml解析器
  private final XPathParser parser;
  //配置文件中的环境
  private String environment;
  //反射器工厂
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();


  //----------------------------------------XMLConfigBuilder构造函数-----------------------------------------------------
  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  //Important
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  //Important
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  //XMLConfigBuilder构造函数
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  //解析Mybaties XML入口方法
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //解析Mybaties XML入口
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 主要解析Mybaties核心配置方法
   * @param root   相当于根节点<configuration></configuration>
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      //解析<properties></properties>节点
      //1.<properties resource="jdbc.properties"/>
      //2.<properties url="jdbc.properties"/>
      /*3.
       *<properties>
          <property name="driver" value="com.mysql.jdbc.Driver" />
          <property name="url" value="jdbc:mysql://localhost:3306/test" />
          <property name="username" value="root" />
          <property name="password" value="root" />
        </properties>
       */
      propertiesElement(root.evalNode("properties"));
      /*
       *<settings>
          <setting name="cacheEnabled" value="true"/>
          <setting name="lazyLoadingEnabled" value="true"/>
          <setting name="multipleResultSetsEnabled" value="true"/>
          <setting name="useColumnLabel" value="true"/>
          <setting name="useGeneratedKeys" value="false"/>
          <setting name="autoMappingBehavior" value="PARTIAL"/>
          <setting name="autoMappingUnknownColumnBehavior" value="WARNING"/>
          <setting name="defaultExecutorType" value="SIMPLE"/>
          <setting name="defaultStatementTimeout" value="30"/>
          <setting name="defaultFetchSize" value="200"/>
          <setting name="safeRowBoundsEnabled" value="false"/>
          <setting name="mapUnderscoreToCamelCase" value="false"/>
          <setting name="localCacheScope" value="SESSION"/>
          <setting name="jdbcTypeForNull" value="OTHER"/>
          <setting name="lazyLoadTriggerMethods" value="equals,clone,hashCode,toString"/>
        </settings>
       */
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      /* 设置自定义VFS */
      //VFS，它提供一个非常简单的API，用于【访问应用程序服务器内的资源】
      /*
       VFS（virtual File System）的作用就是采用标准的Unix系统调用读写位于不同物理介质上的不同文件系统,即为各类文件系统提供了一个统一的操作界面和应用编程接口。
       VFS是一个可以让open()、read()、write()等系统调用不用关心底层的存储介质和文件系统类型就可以工作的粘合层。
       */
      loadCustomVfs(settings);
      /*
       *1. <typeAliases>
             <typeAlias alias="User" type="com.majing.learning.mybatis.entity.User"/>
          </typeAliases>

        2. <typeAliases>
              <package name="com.majing.learning.mybatis.entity"/>
           </typeAliases>
       */
      typeAliasesElement(root.evalNode("typeAliases"));
      /*
       * 1.<plugins>
              <plugin interceptor="com.plugins.interceptors.LogPlugin" />
           </plugins>

         2.<plugins>
             <plugin interceptor="com.bytebeats.mybatis3.interceptor.SQLStatsInterceptor">
                <property name="dialect" value="mysql" />
             </plugin>
          </plugins>
       */
      pluginElement(root.evalNode("plugins"));
      /*
       * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
            <property name="someProperty" value="100"/>
         </objectFactory>
       */
      objectFactoryElement(root.evalNode("objectFactory"));
      //<objectWrapperFactory type="tk.mybatis.MapWrapperFactory"/>
      //被丢弃
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //被丢弃
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      //settings解析
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      //解析environments节点信息
      environmentsElement(root.evalNode("environments"));
      /*
       * <databaseIdProvider type="DB_VENDOR">
           <property name="MySQL" value="mysql"/>
           <property name="Oracle" value="oracle" />
         </databaseIdProvider>
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      /*
       * 1.<typeHandlers>
              <typeHandler handler="cn.cgq.demo.mybatis.typeHandler.MyDemoTypeHandler" javaType="String" jdbcType="INTEGER"/>
           </typeHandlers>

         2.<typeHandlers>
              <package name="org.mybatis.example"/>
           </typeHandlers>
       */
      typeHandlerElement(root.evalNode("typeHandlers"));
      //parser mappers节点
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   *  设置自定义VFS,将用户自定义VFS放入configuration对象中
   *  这里提到了Mybatis VFS，它提供一个非常简单的API，用于【访问应用程序服务器内的资源】。
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          //通过反射获取clazz的类类型
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          //将用户自定义的vfsImpl加入到configuration中
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 通过XMLConfigBuilder的typeAliasesElement方法解析,在该方法内部调用TypeAliasRegistry的registerAlias方法完成注册，并将注册的别名存入本地缓存中。
   * XMLConfigBuilder调用的registerAlias方法并没有什么特别的地方，但TypeAliasRegistry却提供了批量注册别名的方法，该方法只需要一个包名参数。
   * 该方法会在指定的包路径下扫描可注册的类（接口和内部类除外），并以类的简单名为key完整名为value注册别名。
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //获取package的name属性 <package name="com.majing.learning.mybatis.entity"/>
          String typeAliasPackage = child.getStringAttribute("name");
          //TODO 通过VFS,Java反射技术将指定package下的类进行alias处理
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //获取typeAlias的alias、type属性 <typeAlias alias="User" type="com.majing.learning.mybatis.entity.User"/>
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            //利用Java的反射机制获取type="com.majing.learning.mybatis.entity.User"的类类型
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              //alias为空时:1.自动通过反射获取SimpleName. 2.获取注解Alias的value值作为alias别名
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 通过XMLConfigBuilder的pluginElement方法解析,在该方法内部[实例化插件对象]后存入Configuration的interceptorChain变量里
   * <plugin interceptor="com.plugins.interceptors.LogPlugin" />
   * 谨慎使用自定义Plugin拦截器，因为它可能修改Mybatis核心的东西。实现自定义Plugin我们需要实现 Interceptor接口。并未这个类注解@Intercepts。
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        //获取interceptor属性值
        String interceptor = child.getStringAttribute("interceptor");
        //获取property的属性
        Properties properties = child.getChildrenAsProperties();
        //通过Java的反射机制得到interceptor值的类类型并且实例化
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        //将interceptorInstance加入到configuration中的InterceptorChain中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 通过XMLConfigBuilder的objectFactoryElement方法解析
   * 在 MyBatis 中，当其 sql 映射配置文件中的 sql 语句所得到的查询结果，被动态映射到 resultType 或其他处理结果集的参数配置对应的 Java 类型，其中就有 JavaBean 等封装类。而 objectFactory 对象工厂就是用来创建实体对象的类。
     在 MyBatis 中，默认的 objectFactory 要做的就是实例化查询结果对应的目标类，有两种方式可以将查询结果的值映射到对应的目标类，一种是通过目标类的默认构造方法，另外一种就是通过目标类的有参构造方法。
     有时候在 new 一个新对象（构造方法或者有参构造方法），在得到对象之前需要处理一些逻辑，或者在执行该类的有参构造方法时，在传入参数之前，要对参数进行一些处理，这时就可以创建自己的 objectFactory 来加载该类型的对象。
   * 如果要改写默认的对象工厂，可以继承 DefaultObjectFactory 来创建自己的对象工厂
   * MyBatis uses an ObjectFactory to create all needed new Objects.
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //获取type属性值
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      //通过反射获取type类的类类型并实例化
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      //将objectFactory加入到Configuration中的ObjectFactory里面
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 通过XMLConfigBuilder的objectWrapperFactoryElement()解析
   *
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      //获取type属性值
      String type = context.getStringAttribute("type");
      //通过Java反射技术获取type类的类类型
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      //将ObjectWrapperFactory对象加入到Configuration中的ObjectWrapperFactory里面
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 通过XMLConfigBuilder的propertiesElement方法解析，解析后的结果存放在Configuration的variables变量里。
   * 解析顺序，先解析子节点里的属性值，再解析resource属性指定的配置文件里的值。后者会覆盖前者的值。
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //获取子节点属性
      Properties defaults = context.getChildrenAsProperties();
      //获取resources的属性值
      String resource = context.getStringAttribute("resource");
      //获取url的属性值
      String url = context.getStringAttribute("url");
      //url、resource属性不能同时出现
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        //将resource所引用的property文件读取到defaults中
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        //将url所引用的property文件读取到defaults中
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //获取configuration中的variables Properties已有的properties
      Properties vars = configuration.getVariables();
      if (vars != null) {
        //将configuration中已有的proprties加入到defaults中
        defaults.putAll(vars);
      }
      //将属性值存放大到XPathParser中的variables中
      parser.setVariables(defaults);
      //将属性值存放到Configuration中的variables中
      configuration.setVariables(defaults);
    }
  }

  /**
   * 通过XMLConfigBuilder的settingsElement方法解析，解析前校验属性是否是可配置的，只要有一个不可配置，整个mybatis就会异常退出，所以配置这些属性务必小心。
   */
  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 通过XMLConfigBuilder的environmentsElement方法解析，在该方法内，先判断默认是否指定了环境ID，没有的话就使用默认的环境ID吗，然后在各个环境里取ID对应的项。
   * 也就是说一个Configuration只会保存一个数据库环境，如果要配置多数据库环境的话需要创建多个Configuration对象。
   * 在改方法内先解析事务工厂、再解析数据源、然后再解析数据库环境，在解析数据库环境的过程中会访问一次数据库，以取得数据库类型信息。
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          //Parser解析TransactionManager事务配置
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //Parser解析数据源DataSource
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * 通过XMLConfigBuilder的databaseIdProviderElement解析
   * 支持多源数据库
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      //获取type属性值
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      //获取DB_VENDOR的类类型并实例化
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    //获取Environment对象
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      //设置Configuration中的databaseId值
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  //Parser数据源
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      //这里Properties里面存放的就是[driver,url,username,password]的数据库连接信息
      Properties props = context.getChildrenAsProperties();
      //处理dataSource节点的type="POOLED"属性
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 通过XMLConfigBuilder的typeHandlerElement方法解析,在该方法内部调用TypeHandlerRegistry的register方法完成注册，并将注册的类型处理器存入本地缓存中。
   * 同typeAliases一样，TypeHandlerRegistry也提供了批量注册的方法，该方法同样只需要一个包名参数。
   * 该方法会在指定的包路径下扫描可注册的类（接口、抽象类和内部类除外），不过只注册那些配置了MappedTypes注解的类。
   * <typeHandler handler="cn.cgq.demo.mybatis.typeHandler.MyDemoTypeHandler" javaType="String" jdbcType="INTEGER"/>
   * <package name="org.mybatis.example"/>
   */
  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //获取name属性值
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          //获取javaType属性值
          String javaTypeName = child.getStringAttribute("javaType");
          //获取jdbcType属性值
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          //获取handler属性值
          String handlerTypeName = child.getStringAttribute("handler");
          //通过反射获取javaType的类类型
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          //通过反射获取jdbcType的类类型
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          //通过反射获取handlerTypeName的类类型
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 通过XMLConfigBuilder的mapperElement方法解析，在该方法内部通过调用XMLMapperBuilder的parse方法完成。mapper的解析是mybatis的核心功能，涉及的流程较复杂
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {                            // <package name="com.almybaties.dao.mapper"/>
          String mapperPackage = child.getStringAttribute("name");    //获取name属性值
          //TODO ??????
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");     // <mapper  resource="src/main/resources/StudentMapper.xml"/>
          String url = child.getStringAttribute("url");               // <mapper  url="src/main/resources/StudentMapper.xml"/>
          String mapperClass = child.getStringAttribute("class");     // <mapper  class="com.almybaties.dao.mapper.StudentMapper"/>
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            // <mapper  resource="src/main/resources/StudentMapper.xml"/>
            //读取resource路径下的mapper xml文件
            InputStream inputStream = Resources.getResourceAsStream(resource);
            //XMLMapper解析Builder
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            //mapper parser[mapper解析]
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            // <mapper  url="src/main/resources/StudentMapper.xml"/>
            //读取url所代表的mapper xml
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            //mapper解析
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            // <mapper  class="com.almybaties.dao.mapper.StudentMapper"/>
            //通过反射拿到mapper interface接口
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            //将mapper接口添加到MapperRegistry[Mapper注册中心]
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
