package io.github.easyretrofit.spring.boot;

import io.github.easyretrofit.core.resource.RetrofitApiInterfaceBean;
import io.github.easyretrofit.spring.boot.global.RetrofitBuilderGlobalConfig;
import io.github.easyretrofit.spring.boot.global.RetrofitBuilderGlobalConfigProperties;
import io.github.easyretrofit.core.*;
import io.github.easyretrofit.core.generator.RetrofitBuilderGenerator;
import io.github.easyretrofit.core.resource.RetrofitClientBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import retrofit2.Retrofit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrofit Resources Definition and Registry, including Retrofit objects and Retrofit API objects of dynamic proxy
 * Retrofit 资源的注册器，包括 Retrofit 对象和动态代理的 Retrofit API 对象
 * 非常重要，这里是可以通过ConfigurableListableBeanFactory获取到上下文中的bean
 *
 * @author liuziyuan
 */
public class RetrofitResourceDefinitionRegistry implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware, EnvironmentAware {

    Logger log = LoggerFactory.getLogger(RetrofitResourceDefinitionRegistry.class);
    private ApplicationContext applicationContext;

    private Environment environment;

    /**
     * 定义于 BeanDefinitionRegistryPostProcessor 接口中。
     * 在Spring IoC容器注册完所有的Bean Definition（即解析并加载了所有XML配置文件或注解扫描完成之后）但尚未创建任何bean实例之前调用。
     * 通过此方法，开发者可以添加、修改或删除已注册的Bean Definition，比如动态注册额外的bean或者调整原有的bean定义属性。
     *
     * @param beanDefinitionRegistry
     * @throws BeansException
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        // TODO empty method, if you want to definition & registry , use it.
    }

    /**
     * 定义于 BeanFactoryPostProcessor 接口中。
     * 在Spring IoC容器已经完成Bean Definition的注册并且Bean Factory已经创建出来，但在初始化单个bean实例（调用其构造函数或工厂方法）之前调用。
     * 通过此方法，开发者能够访问和修改bean工厂级别的共享配置，例如更改bean定义的属性值（如注入的依赖项）、设置Bean的scope等，但它不适用于改变Bean实例化后的状态。
     *
     * @param beanFactory
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;
        RetrofitResourceContext context;
        try {
            // get RetrofitAnnotationBean
            RetrofitAnnotationBean retrofitAnnotationBean = (RetrofitAnnotationBean) beanFactory.getBean(RetrofitAnnotationBean.class.getName());
            // get RetrofitBuilderExtension
            RetrofitBuilderExtension retrofitBuilderExtension = getRetrofitBuilderExtension(retrofitAnnotationBean.getRetrofitExtension().getRetrofitBuilderClasses());
            // get RetrofitInterceptorExtensions
            List<RetrofitInterceptorExtension> retrofitInterceptorExtensions = getRetrofitInterceptorExtensions(retrofitAnnotationBean.getRetrofitExtension().getRetrofitInterceptorClasses());
            // init RetrofitResourceContext
            context = initRetrofitResourceContext(retrofitAnnotationBean, retrofitBuilderExtension, retrofitInterceptorExtensions);
            // registry RetrofitResourceContext
            registryRetrofitResourceContext(beanDefinitionRegistry, context);
            // get RetrofitClientBean
            Set<RetrofitClientBean> retrofitClientBeanList = context.getRetrofitClients();
            // registry Retrofit object
            registryRetrofitInstance(beanDefinitionRegistry, retrofitClientBeanList, context);
            // registry proxy object of retrofit api interface
            registryRetrofitInterfaceProxy(beanDefinitionRegistry, retrofitClientBeanList);
            // set log
            setLog(context);
        } catch (NoSuchBeanDefinitionException exception) {
            log.info(Constants.RETROFIT_OBJECTS_CREATE_INJECT_ERROR);
            throw exception;
        }
    }

    private List<RetrofitInterceptorExtension> getRetrofitInterceptorExtensions(Set<Class<? extends RetrofitInterceptorExtension>> retrofitInterceptorClasses) {
        List<RetrofitInterceptorExtension> retrofitInterceptorExtensions = new ArrayList<>();
        for (Class<? extends RetrofitInterceptorExtension> retrofitInterceptorClass : retrofitInterceptorClasses) {
            try {
                retrofitInterceptorExtensions.add(retrofitInterceptorClass.newInstance());
            } catch (IllegalAccessException | InstantiationException e) {
                throw new RuntimeException(e);
            }
        }
        return retrofitInterceptorExtensions;
    }

    private RetrofitBuilderExtension getRetrofitBuilderExtension(Set<Class<? extends RetrofitBuilderExtension>> retrofitBuilderClasses) {
        RetrofitBuilderGlobalConfigProperties properties = new RetrofitBuilderGlobalConfigProperties().generate(environment);
        RetrofitBuilderExtension globalConfigExtension = getExtensionRetrofitBuilderExtension(retrofitBuilderClasses);
        return new RetrofitBuilderGlobalConfig(properties, globalConfigExtension);
    }

    private RetrofitBuilderExtension getExtensionRetrofitBuilderExtension(Set<Class<? extends RetrofitBuilderExtension>> retrofitBuilderClasses) {
        retrofitBuilderClasses = retrofitBuilderClasses.stream().filter(clazz -> !clazz.equals(RetrofitBuilderGlobalConfig.class)).collect(Collectors.toSet());
        if (retrofitBuilderClasses.size() > 1) {
            log.warn("There are multiple RetrofitBuilderExtension class, please check your configuration");
            return null;
        } else if (retrofitBuilderClasses.size() == 1) {
            Class<? extends RetrofitBuilderExtension> clazz = new ArrayList<>(retrofitBuilderClasses).get(0);
            try {
                return clazz.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    private void registryRetrofitResourceContext(BeanDefinitionRegistry registry, RetrofitResourceContext context) {
        BeanDefinitionBuilder builder;
        builder = BeanDefinitionBuilder.genericBeanDefinition(RetrofitResourceContext.class, () -> context);
        GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();
        registry.registerBeanDefinition(RetrofitResourceContext.class.getName(), definition);
    }

    private RetrofitResourceContext initRetrofitResourceContext(RetrofitAnnotationBean retrofitAnnotationBean,
                                                                RetrofitBuilderExtension retrofitBuilderExtension,
                                                                List<RetrofitInterceptorExtension> retrofitInterceptorExtensions) {
        EnvManager env = new SpringBootEnv(environment);
        RetrofitResourceContextBuilder retrofitResourceContextBuilder = new RetrofitResourceContextBuilder();
        return retrofitResourceContextBuilder.buildContextInstance(retrofitAnnotationBean.getBasePackages().toArray(new String[0]),
                retrofitAnnotationBean.getRetrofitBuilderClassSet(),
                retrofitBuilderExtension,
                retrofitInterceptorExtensions, env);
    }

    private void registryRetrofitInstance(BeanDefinitionRegistry beanDefinitionRegistry, Set<RetrofitClientBean> retrofitClientBeanList, RetrofitResourceContext context) {
        for (RetrofitClientBean clientBean : retrofitClientBeanList) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(Retrofit.class, () -> {
                RetrofitBuilderGenerator retrofitBuilderGenerator = new RetrofitBuilderGenerator(clientBean, context, new SpringCDIBeanManager(applicationContext));
                final Retrofit.Builder retrofitBuilder = retrofitBuilderGenerator.generate();
                return retrofitBuilder.build();
            });
            GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();
            definition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, clientBean.getRetrofitInstanceName()));
            beanDefinitionRegistry.registerBeanDefinition(clientBean.getRetrofitInstanceName(), definition);
        }
    }

    private void registryRetrofitInterfaceProxy(BeanDefinitionRegistry beanDefinitionRegistry, Set<RetrofitClientBean> retrofitClientBeanList) {
        for (RetrofitClientBean clientBean : retrofitClientBeanList) {
            for (RetrofitApiInterfaceBean serviceBean : clientBean.getRetrofitApiInterfaceBeans()) {
                BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(serviceBean.getSelfClazz());
                GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();
                definition.getConstructorArgumentValues().addGenericArgumentValue(Objects.requireNonNull(definition.getBeanClassName()));
                definition.getConstructorArgumentValues().addGenericArgumentValue(serviceBean);
                definition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, serviceBean.getSelfClazz().getName()));
                definition.setPrimary(true);
                definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
                definition.setBeanClass(RetrofitServiceProxyFactory.class);
                beanDefinitionRegistry.registerBeanDefinition(serviceBean.getSelfClazz().getName(), definition);
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void setLog(RetrofitResourceContext context) {
        RetrofitResourceContextLog retrofitResourceContextLog = new RetrofitResourceContextLog(context);
        RetrofitWebFramewrokInfoBean infoBean = new RetrofitWebFramewrokInfoBean(this.getClass().getPackage().getImplementationTitle(),
                this.getClass().getPackage().getImplementationVersion());
        retrofitResourceContextLog.showLog(infoBean);
    }


    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
