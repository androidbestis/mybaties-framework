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
package org.apache.ibatis.session;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.builder.annotation.MethodResolver;
import org.apache.ibatis.builder.xml.XMLStatementBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.SoftCache;
import org.apache.ibatis.cache.decorators.WeakCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.datasource.jndi.JndiDataSourceFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSourceFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ReuseExecutor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.cglib.CglibProxyFactory;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl;
import org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.LanguageDriverRegistry;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * Configuration对应Mybaties配置文件的标签
 * 解析xml就是初始化Configuration的过程
 */
public class Configuration {

  //--------------------------------------------------- 变量Variables Start----------------------------------------------------------------

  /**
   * Mybaties可以配置成适应多种环境,这种机制有助于将SQL映射应用于多种数据库之中
   * 比如: 设置不同的开发环境、测试、线上配置,在每个配置中可以配置【事务管理器】和【数据源对象】
   */
  protected Environment environment;

  //允许在嵌套语句中使用分页（RowBounds）。如果允许使用则设置为 false
  protected boolean safeRowBoundsEnabled;
  //允许在嵌套语句中使用分页（ResultHandler）。如果允许使用则设置为false
  protected boolean safeResultHandlerEnabled = true;
  //是否自动开启驼峰命名规则（camel case）映射。即从经典数据库列名 A_COLUMN 到经典 Java 属性名 aColumn 的类似映射。
  protected boolean mapUnderscoreToCamelCase;
  //当开启时，任何方法的调用都会加载该对象的所有属性。否则，每个属性会按需加载
  protected boolean aggressiveLazyLoading;
  //是否允许单一语句返回多结果集（需要兼容驱动）
  protected boolean multipleResultSetsEnabled = true;
  //允许 JDBC 支持自动生成主键，需要驱动兼容。 如果设置为 true 则这个设置强制使用自动生成主键，尽管一些驱动不能兼容但仍可正常工作（比如 Derby）。
  protected boolean useGeneratedKeys;
  //使用列标签代替列名。不同的驱动在这方面会有不同的表现
  protected boolean useColumnLabel = true;
  //配置全局性的cache开关
  protected boolean cacheEnabled = true;
  /*指定当结果集中值为 null 的时候是否调用映射对象的 setter（map 对象时为 put）方法，这对于有 Map.keySet() 依赖或 null 值初始化的时候是有用的。
   注意基本类型（int、boolean等）是不能设置成 null 的。*/ //?????????????/
  protected boolean callSettersOnNulls;
  //是否使用实际的参数名称 ??????????
  protected boolean useActualParamName = true;

  protected boolean returnInstanceForEmptyRow;

  //指定Mybaties增加到日志名称的前缀
  protected String logPrefix;
  //指定Mybaties所用日志的具体实现,未指定时将自动查找
  protected Class <? extends Log> logImpl;

  protected Class <? extends VFS> vfsImpl;
   /*
     MyBatis 利用本地缓存机制（Local Cache）防止循环引用（circular references）和加速重复嵌套查询。 
     默认值为 SESSION，这种情况下会缓存一个会话中执行的所有查询。 
     若设置值为 STATEMENT，本地会话仅用在语句执行上，对相同 SqlSession 的不同调用将不会共享数据。
   */
  protected LocalCacheScope localCacheScope = LocalCacheScope.SESSION;
  /*
    当没有为参数提供特定的JDBC类型时，为空值指定JDBC类型。
    某些驱动需要指定列的JDBC类型，多数情况直接用一般类型即可，比如 NULL、VARCHAR 或 OTHER。
  */
  protected JdbcType jdbcTypeForNull = JdbcType.OTHER;
  //指定哪个对象的方法触发一次延迟加载。
  protected Set<String> lazyLoadTriggerMethods = new HashSet<String>(Arrays.asList(new String[] { "equals", "clone", "hashCode", "toString" }));
  //设置超时时间，它决定驱动等待数据库响应的秒数。
  protected Integer defaultStatementTimeout;

  protected Integer defaultFetchSize;
  /* 置默认的执行器。
      SIMPLE 就是普通的执行器；
      REUSE 执行器会重用预处理语句（prepared statements）；
      BATCH 执行器将重用语句并执行批量更新。
   */
  protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
  /**
   * 指定 MyBatis 应如何自动映射列到字段或属性。
   * NONE 表示取消自动映射；
   * PARTIAL 只会自动映射没有定义嵌套结果集映射的结果集。
   * FULL 会自动映射任意复杂的结果集（无论是否嵌套）。
   */
  protected AutoMappingBehavior autoMappingBehavior = AutoMappingBehavior.PARTIAL;
  protected AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior = AutoMappingUnknownColumnBehavior.NONE;
  //这里配置的属性可以在整个配置文件中使用来替换需要动态配置的属性值
  protected Properties variables = new Properties();
  //ReflectorFactory 、ObjectFactory 、ObjectWrapperFactory  ： 创建 MetaObject所需
  protected ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
  //对象创建工厂，默认的实现类DefaultObjectFactory，用来创建对象，比如传入List.class，利用反射返回ArrayList的实例
  protected ObjectFactory objectFactory = new DefaultObjectFactory();
  //对象包装工厂，默认实现类是DefaultObjectWrapperFactory，包装Object实例
  protected ObjectWrapperFactory objectWrapperFactory = new DefaultObjectWrapperFactory();
  //延迟加载的全局开关。当开启时，所有关联对象都会延迟加载。 特定关联关系中可通过设置fetchType属性来覆盖该项的开关状态。
  protected boolean lazyLoadingEnabled = false;
  //指定 Mybatis 创建具有延迟加载能力的对象所用到的代理工具
  //为查询结果添加延迟加载功能的代理工厂，需开启 lazyLoadingEnabled、aggressiveLazyLoading参数
  protected ProxyFactory proxyFactory = new JavassistProxyFactory(); // #224 Using internal Javassist instead of OGNL
  //数据库类型id，MyBatis 可以根据不同的数据库厂商执行不同的语句，这种多厂商的支持是基于映射语句中的 databaseId 属性
  protected String databaseId;
  /**
   * Configuration factory class.
   * Used to create Configuration for loading deserialized unread properties.
   *
   * @see <a href='https://code.google.com/p/mybatis/issues/detail?id=300'>Issue 300 (google code)</a>
   */
  protected Class<?> configurationFactory;
  //注册Mapper  Mapper注册中心
  protected final MapperRegistry mapperRegistry = new MapperRegistry(this);
  //拦截器链     用户自定义拦截器的执行链，能够拦截:Executor、StatementHandler、ParameterHandler、ResultSetHandler四种类型的方法调用
  protected final InterceptorChain interceptorChain = new InterceptorChain();
  //TypeHandler 注册  类型注册中心,TypeHandler 是能够从ResultSet取出特定类型的接口
  protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();
  //[别名]和[具体类] 注册   别名注册中心
  protected final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
  //这个是[指定解析的驱动]，比如你可以使用velocity模板引擎来替代xml文件，默认是XMLLanguageDriver，也就是使用xml文件来写sql语句
  //根据xml 为 mappedStatement 生成 SqlSource， 使其可以从指定的参数获得 BoundSql
  protected final LanguageDriverRegistry languageRegistry = new LanguageDriverRegistry();

  //对应 Mapper.xml 里配置的[Statement]
  protected final Map<String, MappedStatement> mappedStatements = new StrictMap<MappedStatement>("Mapped Statements collection");
  //对应 Mapper.xml 里配置的[cache]
  protected final Map<String, Cache> caches = new StrictMap<Cache>("Caches collection");
  //对应 Mapper.xml 里的[ResultMap]
  protected final Map<String, ResultMap> resultMaps = new StrictMap<ResultMap>("Result Maps collection");
  //对应 Mapper.xml 里的[ParameterMap]
  protected final Map<String, ParameterMap> parameterMaps = new StrictMap<ParameterMap>("Parameter Maps collection");
  //主键生成器
  protected final Map<String, KeyGenerator> keyGenerators = new StrictMap<KeyGenerator>("Key Generators collection");
  //存储已经加载过的mapper xml资源，见MapperAnnotationBuilder#loadXmlResource
  protected final Set<String> loadedResources = new HashSet<String>();
  //存储已经解析过的mapper对应的xml节点
  protected final Map<String, XNode> sqlFragments = new StrictMap<XNode>("XML fragments parsed from previous mappers");
  //存储所有未处理的mapper对应的xml节点
  protected final Collection<XMLStatementBuilder> incompleteStatements = new LinkedList<XMLStatementBuilder>();
  //存储所有未处理的缓存信息
  protected final Collection<CacheRefResolver> incompleteCacheRefs = new LinkedList<CacheRefResolver>();
  //存储所有未处理ResultMap的映射信息
  protected final Collection<ResultMapResolver> incompleteResultMaps = new LinkedList<ResultMapResolver>();

  protected final Collection<MethodResolver> incompleteMethods = new LinkedList<MethodResolver>();

  /*
   * A map holds cache-ref relationship. The key is the namespace that
   * references a cache bound to another namespace and the value is the
   * namespace which the actual cache is bound to.
   */
  protected final Map<String, String> cacheRefMap = new HashMap<String, String>();

  //--------------------------------------------------- 变量Variables End----------------------------------------------------------------

  //-----------------------------------------------构造Construct Start---------------------------------------------------
  //带environment的构造函数
  public Configuration(Environment environment) {
    this();
    this.environment = environment;
  }

  ////通过使用TypeAliasRegistry来注册一些类的别名
  //无参构造
  public Configuration() {
    //事务管理的2种方式:JDBC--Managed类型
    typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class);
    typeAliasRegistry.registerAlias("MANAGED", ManagedTransactionFactory.class);

    //数据源type类型: JNDI--POOLED--UNPOOLED
    typeAliasRegistry.registerAlias("JNDI", JndiDataSourceFactory.class);
    typeAliasRegistry.registerAlias("POOLED", PooledDataSourceFactory.class);
    typeAliasRegistry.registerAlias("UNPOOLED", UnpooledDataSourceFactory.class);

    //Cache缓存的eviction(驱逐,赶出,回收): 回收策略:
    typeAliasRegistry.registerAlias("PERPETUAL", PerpetualCache.class);
    typeAliasRegistry.registerAlias("FIFO", FifoCache.class);
    typeAliasRegistry.registerAlias("LRU", LruCache.class);
    typeAliasRegistry.registerAlias("SOFT", SoftCache.class);
    typeAliasRegistry.registerAlias("WEAK", WeakCache.class);

    //多数据源厂商Type<databaseIdProvider type="DB_VENDOR">类型
    typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);

    //语言驱动别名类型
    typeAliasRegistry.registerAlias("XML", XMLLanguageDriver.class);
    typeAliasRegistry.registerAlias("RAW", RawLanguageDriver.class);

    //日志别名类型
    typeAliasRegistry.registerAlias("SLF4J", Slf4jImpl.class);
    typeAliasRegistry.registerAlias("COMMONS_LOGGING", JakartaCommonsLoggingImpl.class);
    typeAliasRegistry.registerAlias("LOG4J", Log4jImpl.class);
    typeAliasRegistry.registerAlias("LOG4J2", Log4j2Impl.class);
    typeAliasRegistry.registerAlias("JDK_LOGGING", Jdk14LoggingImpl.class);
    typeAliasRegistry.registerAlias("STDOUT_LOGGING", StdOutImpl.class);
    typeAliasRegistry.registerAlias("NO_LOGGING", NoLoggingImpl.class);

    //代理类型
    typeAliasRegistry.registerAlias("CGLIB", CglibProxyFactory.class);
    typeAliasRegistry.registerAlias("JAVASSIST", JavassistProxyFactory.class);

    languageRegistry.setDefaultDriverClass(XMLLanguageDriver.class);
    languageRegistry.register(RawLanguageDriver.class);
  }

  //-----------------------------------------------构造Construct End---------------------------------------------------


  //-----------------------------------------------getter and setter Start--------------------------------------------
  //获取Log日志前缀Prefix
  public String getLogPrefix() {
    return logPrefix;
  }

  //设置Log日志前缀Prefix
  public void setLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
  }

  //获取Mybaties所用日志的具体实现
  public Class<? extends Log> getLogImpl() {
    return logImpl;
  }

  //设置Mybaties所用日志的具体实现
  public void setLogImpl(Class<? extends Log> logImpl) {
    if (logImpl != null) {
      this.logImpl = logImpl;
      LogFactory.useCustomLogging(this.logImpl);
    }
  }

  //获取Mybaties所使用的VFS具体实现
  public Class<? extends VFS> getVfsImpl() {
    return this.vfsImpl;
  }

  //设置Mybaties所使用的VFS具体实现
  public void setVfsImpl(Class<? extends VFS> vfsImpl) {
    if (vfsImpl != null) {
      this.vfsImpl = vfsImpl;
      VFS.addImplClass(this.vfsImpl);
    }
  }

  //getter
  public boolean isCallSettersOnNulls() {
    return callSettersOnNulls;
  }

  //setter
  public void setCallSettersOnNulls(boolean callSettersOnNulls) {
    this.callSettersOnNulls = callSettersOnNulls;
  }

  //getter
  public boolean isUseActualParamName() {
    return useActualParamName;
  }

  //setter
  public void setUseActualParamName(boolean useActualParamName) {
    this.useActualParamName = useActualParamName;
  }

  //getter
  public boolean isReturnInstanceForEmptyRow() {
    return returnInstanceForEmptyRow;
  }

  //setter
  public void setReturnInstanceForEmptyRow(boolean returnEmptyInstance) {
    this.returnInstanceForEmptyRow = returnEmptyInstance;
  }

  //getter
  public String getDatabaseId() {
    return databaseId;
  }

  //setter
  public void setDatabaseId(String databaseId) {
    this.databaseId = databaseId;
  }

  //getter
  public Class<?> getConfigurationFactory() {
    return configurationFactory;
  }

  //setter
  public void setConfigurationFactory(Class<?> configurationFactory) {
    this.configurationFactory = configurationFactory;
  }

  //getter
  public boolean isSafeResultHandlerEnabled() {
    return safeResultHandlerEnabled;
  }

  //setter
  public void setSafeResultHandlerEnabled(boolean safeResultHandlerEnabled) {
    this.safeResultHandlerEnabled = safeResultHandlerEnabled;
  }

  //getter
  public boolean isSafeRowBoundsEnabled() {
    return safeRowBoundsEnabled;
  }

  //setter
  public void setSafeRowBoundsEnabled(boolean safeRowBoundsEnabled) {
    this.safeRowBoundsEnabled = safeRowBoundsEnabled;
  }

  //getter
  public boolean isMapUnderscoreToCamelCase() {
    return mapUnderscoreToCamelCase;
  }

  //setter
  public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
    this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
  }


  public void addLoadedResource(String resource) {
    loadedResources.add(resource);
  }

  public boolean isResourceLoaded(String resource) {
    return loadedResources.contains(resource);
  }

  //getter
  public Environment getEnvironment() {
    return environment;
  }

  //setter
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  //getter
  public AutoMappingBehavior getAutoMappingBehavior() {
    return autoMappingBehavior;
  }

  //setter
  public void setAutoMappingBehavior(AutoMappingBehavior autoMappingBehavior) {
    this.autoMappingBehavior = autoMappingBehavior;
  }

  /**
   * @since 3.4.0
   */
  //getter
  public AutoMappingUnknownColumnBehavior getAutoMappingUnknownColumnBehavior() {
    return autoMappingUnknownColumnBehavior;
  }

  /**
   * @since 3.4.0
   */
  //setter
  public void setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior) {
    this.autoMappingUnknownColumnBehavior = autoMappingUnknownColumnBehavior;
  }

  //getter
  public boolean isLazyLoadingEnabled() {
    return lazyLoadingEnabled;
  }

  //setter
  public void setLazyLoadingEnabled(boolean lazyLoadingEnabled) {
    this.lazyLoadingEnabled = lazyLoadingEnabled;
  }

  //getter
  public ProxyFactory getProxyFactory() {
    return proxyFactory;
  }

  //setter
  public void setProxyFactory(ProxyFactory proxyFactory) {
    if (proxyFactory == null) {
      proxyFactory = new JavassistProxyFactory();
    }
    this.proxyFactory = proxyFactory;
  }

  //getter
  public boolean isAggressiveLazyLoading() {
    return aggressiveLazyLoading;
  }

  //setter
  public void setAggressiveLazyLoading(boolean aggressiveLazyLoading) {
    this.aggressiveLazyLoading = aggressiveLazyLoading;
  }

  //getter
  public boolean isMultipleResultSetsEnabled() {
    return multipleResultSetsEnabled;
  }

  //setter
  public void setMultipleResultSetsEnabled(boolean multipleResultSetsEnabled) {
    this.multipleResultSetsEnabled = multipleResultSetsEnabled;
  }

  //getter
  public Set<String> getLazyLoadTriggerMethods() {
    return lazyLoadTriggerMethods;
  }

  //setter
  public void setLazyLoadTriggerMethods(Set<String> lazyLoadTriggerMethods) {
    this.lazyLoadTriggerMethods = lazyLoadTriggerMethods;
  }

  //getter
  public boolean isUseGeneratedKeys() {
    return useGeneratedKeys;
  }

  //setter
  public void setUseGeneratedKeys(boolean useGeneratedKeys) {
    this.useGeneratedKeys = useGeneratedKeys;
  }

  //getter
  public ExecutorType getDefaultExecutorType() {
    return defaultExecutorType;
  }

  //setter
  public void setDefaultExecutorType(ExecutorType defaultExecutorType) {
    this.defaultExecutorType = defaultExecutorType;
  }

  //getter
  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  //setter
  public void setCacheEnabled(boolean cacheEnabled) {
    this.cacheEnabled = cacheEnabled;
  }

  //getter
  public Integer getDefaultStatementTimeout() {
    return defaultStatementTimeout;
  }

  //setter
  public void setDefaultStatementTimeout(Integer defaultStatementTimeout) {
    this.defaultStatementTimeout = defaultStatementTimeout;
  }

  /**
   * @since 3.3.0
   */
  //getter
  public Integer getDefaultFetchSize() {
    return defaultFetchSize;
  }

  /**
   * @since 3.3.0
   */
  //setter
  public void setDefaultFetchSize(Integer defaultFetchSize) {
    this.defaultFetchSize = defaultFetchSize;
  }

  //getter
  public boolean isUseColumnLabel() {
    return useColumnLabel;
  }

  //setter
  public void setUseColumnLabel(boolean useColumnLabel) {
    this.useColumnLabel = useColumnLabel;
  }

  //getter
  public LocalCacheScope getLocalCacheScope() {
    return localCacheScope;
  }

  //setter
  public void setLocalCacheScope(LocalCacheScope localCacheScope) {
    this.localCacheScope = localCacheScope;
  }

  //getter
  public JdbcType getJdbcTypeForNull() {
    return jdbcTypeForNull;
  }

  //setter
  public void setJdbcTypeForNull(JdbcType jdbcTypeForNull) {
    this.jdbcTypeForNull = jdbcTypeForNull;
  }

  //getter
  public Properties getVariables() {
    return variables;
  }

  //setter
  public void setVariables(Properties variables) {
    this.variables = variables;
  }

  //getter
  public TypeHandlerRegistry getTypeHandlerRegistry() {
    return typeHandlerRegistry;
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  //setter
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    if (typeHandler != null) {
      getTypeHandlerRegistry().setDefaultEnumTypeHandler(typeHandler);
    }
  }

  //getter
  public TypeAliasRegistry getTypeAliasRegistry() {
    return typeAliasRegistry;
  }

  /**
   * @since 3.2.2
   */
  //getter
  public MapperRegistry getMapperRegistry() {
    return mapperRegistry;
  }

  //getter
  public ReflectorFactory getReflectorFactory() {
	  return reflectorFactory;
  }

  //setter
  public void setReflectorFactory(ReflectorFactory reflectorFactory) {
	  this.reflectorFactory = reflectorFactory;
  }

  //getter
  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  //setter
  public void setObjectFactory(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory;
  }

  //getter
  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  //setter
  public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
    this.objectWrapperFactory = objectWrapperFactory;
  }

  /**
   * @since 3.2.2
   */
  //getter
  public List<Interceptor> getInterceptors() {
    return interceptorChain.getInterceptors();
  }

  //getter
  public LanguageDriverRegistry getLanguageRegistry() {
    return languageRegistry;
  }

  //setter
  public void setDefaultScriptingLanguage(Class<?> driver) {
    if (driver == null) {
      driver = XMLLanguageDriver.class;
    }
    getLanguageRegistry().setDefaultDriverClass(driver);
  }

  //getter
  public LanguageDriver getDefaultScriptingLanguageInstance() {
    return languageRegistry.getDefaultDriver();
  }

  /** @deprecated Use {@link #getDefaultScriptingLanguageInstance()} */
  @Deprecated
  //getter
  public LanguageDriver getDefaultScriptingLanuageInstance() {
    return getDefaultScriptingLanguageInstance();
  }


  //-----------------------------------------------getter and setter End--------------------------------------------

  //创建MetaObject
  public MetaObject newMetaObject(Object object) {
    return MetaObject.forObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  //创建ParameterHandler对象
  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
    parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
    return parameterHandler;
  }

  //创建ResultSetHandler对象
  public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement, RowBounds rowBounds, ParameterHandler parameterHandler,
      ResultHandler resultHandler, BoundSql boundSql) {
    ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);
    resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);
    return resultSetHandler;
  }

  //创建StatementHandler对象
  public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
    statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
    return statementHandler;
  }

  //创建Executor（执行器）对象
  public Executor newExecutor(Transaction transaction) {
    return newExecutor(transaction, defaultExecutorType);
  }

  /**
   * 创建Executor执行器
   */
  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    Executor executor;
    if (ExecutorType.BATCH == executorType) {
      //创建Batch批处理Executor执行器
      executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
      //创建Reuse复用Executor执行器
      executor = new ReuseExecutor(this, transaction);
    } else {
      //创建Simple简单Executor执行器
      executor = new SimpleExecutor(this, transaction);
    }
    //是否开启了全局性的缓存cache
    if (cacheEnabled) {
      //创建Caching缓存Executor执行器
      executor = new CachingExecutor(executor);
    }
    //加载所有的Plugins插件
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
  }

  public void addKeyGenerator(String id, KeyGenerator keyGenerator) {
    keyGenerators.put(id, keyGenerator);
  }

  public Collection<String> getKeyGeneratorNames() {
    return keyGenerators.keySet();
  }

  public Collection<KeyGenerator> getKeyGenerators() {
    return keyGenerators.values();
  }

  public KeyGenerator getKeyGenerator(String id) {
    return keyGenerators.get(id);
  }

  public boolean hasKeyGenerator(String id) {
    return keyGenerators.containsKey(id);
  }

  public void addCache(Cache cache) {
    caches.put(cache.getId(), cache);
  }

  public Collection<String> getCacheNames() {
    return caches.keySet();
  }

  public Collection<Cache> getCaches() {
    return caches.values();
  }

  public Cache getCache(String id) {
    return caches.get(id);
  }

  public boolean hasCache(String id) {
    return caches.containsKey(id);
  }

  public void addResultMap(ResultMap rm) {
    resultMaps.put(rm.getId(), rm);
    checkLocallyForDiscriminatedNestedResultMaps(rm);
    checkGloballyForDiscriminatedNestedResultMaps(rm);
  }

  public Collection<String> getResultMapNames() {
    return resultMaps.keySet();
  }

  public Collection<ResultMap> getResultMaps() {
    return resultMaps.values();
  }

  public ResultMap getResultMap(String id) {
    return resultMaps.get(id);
  }

  public boolean hasResultMap(String id) {
    return resultMaps.containsKey(id);
  }

  public void addParameterMap(ParameterMap pm) {
    parameterMaps.put(pm.getId(), pm);
  }

  public Collection<String> getParameterMapNames() {
    return parameterMaps.keySet();
  }

  public Collection<ParameterMap> getParameterMaps() {
    return parameterMaps.values();
  }

  public ParameterMap getParameterMap(String id) {
    return parameterMaps.get(id);
  }

  public boolean hasParameterMap(String id) {
    return parameterMaps.containsKey(id);
  }

  public void addMappedStatement(MappedStatement ms) {
    mappedStatements.put(ms.getId(), ms);
  }

  public Collection<String> getMappedStatementNames() {
    buildAllStatements();
    return mappedStatements.keySet();
  }

  public Collection<MappedStatement> getMappedStatements() {
    buildAllStatements();
    return mappedStatements.values();
  }

  public Collection<XMLStatementBuilder> getIncompleteStatements() {
    return incompleteStatements;
  }

  public void addIncompleteStatement(XMLStatementBuilder incompleteStatement) {
    incompleteStatements.add(incompleteStatement);
  }

  public Collection<CacheRefResolver> getIncompleteCacheRefs() {
    return incompleteCacheRefs;
  }

  public void addIncompleteCacheRef(CacheRefResolver incompleteCacheRef) {
    incompleteCacheRefs.add(incompleteCacheRef);
  }

  public Collection<ResultMapResolver> getIncompleteResultMaps() {
    return incompleteResultMaps;
  }

  public void addIncompleteResultMap(ResultMapResolver resultMapResolver) {
    incompleteResultMaps.add(resultMapResolver);
  }

  public void addIncompleteMethod(MethodResolver builder) {
    incompleteMethods.add(builder);
  }

  public Collection<MethodResolver> getIncompleteMethods() {
    return incompleteMethods;
  }

  public MappedStatement getMappedStatement(String id) {
    return this.getMappedStatement(id, true);
  }

  public MappedStatement getMappedStatement(String id, boolean validateIncompleteStatements) {
    if (validateIncompleteStatements) {
      buildAllStatements();
    }
    return mappedStatements.get(id);
  }

  public Map<String, XNode> getSqlFragments() {
    return sqlFragments;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptorChain.addInterceptor(interceptor);
  }

  public void addMappers(String packageName, Class<?> superType) {
    mapperRegistry.addMappers(packageName, superType);
  }

  public void addMappers(String packageName) {
    mapperRegistry.addMappers(packageName);
  }

  public <T> void addMapper(Class<T> type) {
    mapperRegistry.addMapper(type);
  }

  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    return mapperRegistry.getMapper(type, sqlSession);
  }

  public boolean hasMapper(Class<?> type) {
    return mapperRegistry.hasMapper(type);
  }

  public boolean hasStatement(String statementName) {
    return hasStatement(statementName, true);
  }

  public boolean hasStatement(String statementName, boolean validateIncompleteStatements) {
    if (validateIncompleteStatements) {
      buildAllStatements();
    }
    return mappedStatements.containsKey(statementName);
  }

  public void addCacheRef(String namespace, String referencedNamespace) {
    cacheRefMap.put(namespace, referencedNamespace);
  }

  /*
   * Parses all the unprocessed statement nodes in the cache. It is recommended
   * to call this method once all the mappers are added as it provides fail-fast
   * statement validation.
   */
  protected void buildAllStatements() {
    if (!incompleteResultMaps.isEmpty()) {
      synchronized (incompleteResultMaps) {
        // This always throws a BuilderException.
        incompleteResultMaps.iterator().next().resolve();
      }
    }
    if (!incompleteCacheRefs.isEmpty()) {
      synchronized (incompleteCacheRefs) {
        // This always throws a BuilderException.
        incompleteCacheRefs.iterator().next().resolveCacheRef();
      }
    }
    if (!incompleteStatements.isEmpty()) {
      synchronized (incompleteStatements) {
        // This always throws a BuilderException.
        incompleteStatements.iterator().next().parseStatementNode();
      }
    }
    if (!incompleteMethods.isEmpty()) {
      synchronized (incompleteMethods) {
        // This always throws a BuilderException.
        incompleteMethods.iterator().next().resolve();
      }
    }
  }

  /*
   * Extracts namespace from fully qualified statement id.
   *
   * @param statementId
   * @return namespace or null when id does not contain period.
   */
  protected String extractNamespace(String statementId) {
    int lastPeriod = statementId.lastIndexOf('.');
    return lastPeriod > 0 ? statementId.substring(0, lastPeriod) : null;
  }

  // Slow but a one time cost. A better solution is welcome.
  protected void checkGloballyForDiscriminatedNestedResultMaps(ResultMap rm) {
    if (rm.hasNestedResultMaps()) {
      for (Map.Entry<String, ResultMap> entry : resultMaps.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof ResultMap) {
          ResultMap entryResultMap = (ResultMap) value;
          if (!entryResultMap.hasNestedResultMaps() && entryResultMap.getDiscriminator() != null) {
            Collection<String> discriminatedResultMapNames = entryResultMap.getDiscriminator().getDiscriminatorMap().values();
            if (discriminatedResultMapNames.contains(rm.getId())) {
              entryResultMap.forceNestedResultMaps();
            }
          }
        }
      }
    }
  }

  // Slow but a one time cost. A better solution is welcome.
  protected void checkLocallyForDiscriminatedNestedResultMaps(ResultMap rm) {
    if (!rm.hasNestedResultMaps() && rm.getDiscriminator() != null) {
      for (Map.Entry<String, String> entry : rm.getDiscriminator().getDiscriminatorMap().entrySet()) {
        String discriminatedResultMapName = entry.getValue();
        if (hasResultMap(discriminatedResultMapName)) {
          ResultMap discriminatedResultMap = resultMaps.get(discriminatedResultMapName);
          if (discriminatedResultMap.hasNestedResultMaps()) {
            rm.forceNestedResultMaps();
            break;
          }
        }
      }
    }
  }

  //自己封装的Map 继承HashMap
  protected static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -4950446264854982944L;
    private final String name;

    public StrictMap(String name, int initialCapacity, float loadFactor) {
      super(initialCapacity, loadFactor);
      this.name = name;
    }

    public StrictMap(String name, int initialCapacity) {
      super(initialCapacity);
      this.name = name;
    }

    public StrictMap(String name) {
      super();
      this.name = name;
    }

    public StrictMap(String name, Map<String, ? extends V> m) {
      super(m);
      this.name = name;
    }

    @SuppressWarnings("unchecked")
    public V put(String key, V value) {
      if (containsKey(key)) {
        throw new IllegalArgumentException(name + " already contains value for " + key);
      }
      if (key.contains(".")) {
        final String shortKey = getShortName(key);
        if (super.get(shortKey) == null) {
          super.put(shortKey, value);
        } else {
          super.put(shortKey, (V) new Ambiguity(shortKey));
        }
      }
      return super.put(key, value);
    }

    public V get(Object key) {
      V value = super.get(key);
      if (value == null) {
        throw new IllegalArgumentException(name + " does not contain value for " + key);
      }
      if (value instanceof Ambiguity) {
        throw new IllegalArgumentException(((Ambiguity) value).getSubject() + " is ambiguous in " + name
            + " (try using the full name including the namespace, or rename one of the entries)");
      }
      return value;
    }

    private String getShortName(String key) {
      final String[] keyParts = key.split("\\.");
      return keyParts[keyParts.length - 1];
    }

    protected static class Ambiguity {
      final private String subject;

      public Ambiguity(String subject) {
        this.subject = subject;
      }

      public String getSubject() {
        return subject;
      }
    }
  }

}
