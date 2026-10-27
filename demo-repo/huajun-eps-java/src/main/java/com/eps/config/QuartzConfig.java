package com.eps.config;

import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

/**
 * Quartz Scheduler configuration for EPS batch job processing.
 * <p>
 * This configuration sets up the Quartz scheduler with Spring integration,
 * enabling scheduled jobs for:
 * </p>
 * <ul>
 *   <li>Payment reconciliation — daily at 2:00 AM</li>
 *   <li>Liquidity monitoring — every 15 minutes</li>
 *   <li>GL batch posting — daily at 1:00 AM</li>
 *   <li>Refund processing — every 30 minutes</li>
 *   <li>Compliance batch screening — daily at 4:00 AM</li>
 * </ul>
 *
 * <p>
 * In the dev profile, an in-memory job store is used. In production,
 * a JDBC-backed store with PostgreSQL is configured for cluster support.
 * </p>
 *
 * @see JobFactory
 * @see SchedulerFactoryBean
 */
@Configuration
public class QuartzConfig {

    /**
     * Creates a Spring-aware JobFactory that autowires job instances
     * from the Spring application context.
     *
     * @param applicationContext the Spring application context
     * @return a SpringBeanJobFactory instance
     */
    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * Creates and configures the Quartz SchedulerFactoryBean.
     * <p>
     * This bean is the central Quartz integration point. It reads Quartz
     * properties from the Spring Boot configuration (spring.quartz.*),
     * sets up the job factory for Spring DI, and configures the scheduler.
     * </p>
     *
     * @param jobFactory the Spring-aware job factory
     * @param dataSource the datasource for JDBC job store (prod only)
     * @return a fully configured SchedulerFactoryBean
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            SpringBeanJobFactory jobFactory,
            @Qualifier("dataSource") DataSource dataSource) throws IOException {

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setDataSource(dataSource);

        // Load quartz properties from application.yml via Spring Boot autoconfig
        PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
        propertiesFactoryBean.setLocation(new ClassPathResource("application.yml"));
        propertiesFactoryBean.afterPropertiesSet();
        Properties quartzProperties = propertiesFactoryBean.getObject();

        if (quartzProperties != null) {
            factory.setQuartzProperties(extractQuartzProperties(quartzProperties));
        }

        // Wait for jobs to complete on shutdown
        factory.setWaitForJobsToCompleteOnShutdown(true);

        // Overwrite existing jobs on re-deployment
        factory.setOverwriteExistingJobs(true);

        // Start the scheduler after a short delay to allow the context to fully initialize
        factory.setStartupDelay(5);

        return factory;
    }

    /**
     * Extracts Quartz-specific properties from the full application properties.
     *
     * @param allProperties all application properties
     * @return filtered Quartz properties
     */
    private Properties extractQuartzProperties(Properties allProperties) {
        Properties quartzProps = new Properties();
        String prefix = "spring.quartz.properties.org.quartz.";
        allProperties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith(prefix)) {
                quartzProps.setProperty(
                        keyStr.substring(prefix.length()),
                        value.toString());
            }
        });
        return quartzProps;
    }

    /**
     * Custom SpringBeanJobFactory that provides autowiring support for
     * Quartz job instances, allowing them to use Spring dependency injection.
     */
    static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {

        private ApplicationContext applicationContext;

        /**
         * Sets the application context for autowiring.
         *
         * @param applicationContext the Spring application context
         */
        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        /**
         * Creates a new job instance, autowiring its dependencies.
         *
         * @param bundle the trigger fired bundle
         * @return the job instance with autowired dependencies
         * @throws Exception if job creation fails
         */
        @Override
        protected Object createJobInstance(
                org.quartz.spi.TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            applicationContext.getAutowireCapableBeanFactory().autowireBean(job);
            return job;
        }
    }
}
