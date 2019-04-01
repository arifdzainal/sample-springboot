/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.validation;

import javax.validation.ValidationException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * {@link Validator} implementation that delegates calls to another {@link Validator}.
 * This {@link Validator} implements Spring's {@link SmartValidator} interface but does
 * not implement the JSR-303 {@code javax.validator.Validator} interface.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @since 2.0.0
 */
public class ValidatorAdapter implements SmartValidator, ApplicationContextAware,
		InitializingBean, DisposableBean {

	private final SmartValidator target;

	private final boolean existingBean;

	ValidatorAdapter(SmartValidator target, boolean existingBean) {
		this.target = target;
		this.existingBean = existingBean;
	}

	public final Validator getTarget() {
		return this.target;
	}

	@Override
	public boolean supports(Class<?> clazz) {
		return this.target.supports(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		this.target.validate(target, errors);
	}

	@Override
	public void validate(Object target, Errors errors, Object... validationHints) {
		this.target.validate(target, errors, validationHints);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		if (!this.existingBean && this.target instanceof ApplicationContextAware) {
			((ApplicationContextAware) this.target)
					.setApplicationContext(applicationContext);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (!this.existingBean && this.target instanceof InitializingBean) {
			((InitializingBean) this.target).afterPropertiesSet();
		}
	}

	@Override
	public void destroy() throws Exception {
		if (!this.existingBean && this.target instanceof DisposableBean) {
			((DisposableBean) this.target).destroy();
		}
	}

	/**
	 * Return a {@link Validator} that only implements the {@link Validator} interface,
	 * wrapping it if necessary.
	 * <p>
	 * If the specified {@link Validator} is not {@code null}, it is wrapped. If not, a
	 * {@link javax.validation.Validator} is retrieved from the context and wrapped.
	 * Otherwise, a new default validator is created.
	 * @param applicationContext the application context
	 * @param validator an existing validator to use or {@code null}
	 * @return the validator to use
	 */
	public static Validator get(ApplicationContext applicationContext,
			Validator validator) {
		if (validator != null) {
			return wrap(validator, false);
		}
		return getExistingOrCreate(applicationContext);
	}

	private static Validator getExistingOrCreate(ApplicationContext applicationContext) {
		Validator existing = getExisting(applicationContext);
		if (existing != null) {
			return wrap(existing, true);
		}
		return create();
	}

	private static Validator getExisting(ApplicationContext applicationContext) {
		try {
			javax.validation.Validator validator = applicationContext
					.getBean(javax.validation.Validator.class);
			if (validator instanceof Validator) {
				return (Validator) validator;
			}
			return new SpringValidatorAdapter(validator);
		}
		catch (NoSuchBeanDefinitionException ex) {
			return null;
		}
	}

	private static Validator create() {
		OptionalValidatorFactoryBean validator = new OptionalValidatorFactoryBean();
		try {
			validator
					.setMessageInterpolator(new MessageInterpolatorFactory().getObject());
		}
		catch (ValidationException ex) {
			// Ignore
		}
		return wrap(validator, false);
	}

	private static Validator wrap(Validator validator, boolean existingBean) {
		if (validator instanceof javax.validation.Validator) {
			if (validator instanceof SpringValidatorAdapter) {
				return new ValidatorAdapter((SpringValidatorAdapter) validator,
						existingBean);
			}
			return new ValidatorAdapter(
					new SpringValidatorAdapter((javax.validation.Validator) validator),
					existingBean);
		}
		return validator;
	}

}
