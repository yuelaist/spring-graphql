/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.graphql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import graphql.ExecutionInput;
import graphql.execution.ExecutionId;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Common representation for GraphQL request input. This can be converted to
 * {@link ExecutionInput} via {@link #toExecutionInput(boolean)} and the
 * {@code ExecutionInput} further customized via
 * {@link #configureExecutionInput(BiFunction)}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class RequestInput {

	private final String query;

	@Nullable
	private final String operationName;

	private final Map<String, Object> variables;

	@Nullable
	private final Locale locale;

	private final String id;

	private final List<BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput>> executionInputConfigurers = new ArrayList<>();

	@Nullable
	private ExecutionId executionId;

	/**
	 * Create an instance.
	 * @param query the query, mutation, or subscription for the request
	 * @param operationName an optional, explicit name assigned to the query
	 * @param  variables variables by which the query is parameterized
	 * @param locale the locale associated with the request, if any
	 * @param id the request id, to be used as the {@link ExecutionId}
	 */
	public RequestInput(
			String query, @Nullable String operationName, @Nullable Map<String, Object> variables,
			@Nullable Locale locale, String id) {

		Assert.notNull(query, "'query' is required");
		Assert.notNull(id, "'id' is required");
		this.query = query;
		this.operationName = operationName;
		this.variables = ((variables != null) ? variables : Collections.emptyMap());
		this.locale = locale;
		this.id = id;
	}


	/**
	 * Return an identifier for the request. This id can be later propagated
	 * as the {@link ExecutionId} if {@link #executionId(ExecutionId) none has been set}.
	 * <p>For web transports, this identifier can be used to correlate
	 * request and response messages on a multiplexed connection.
	 * @return the request id.
	 * @see <a href="https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md">GraphQL over WebSocket Protocol</a>
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Return the query, mutation, or subscription for the request.
	 * @return the query, a non-empty string.
	 */
	public String getQuery() {
		return this.query;
	}

	/**
	 * Return the explicitly assigned name for the query.
	 * @return the operation name or {@code null}.
	 */
	@Nullable
	public String getOperationName() {
		return this.operationName;
	}

	/**
	 * Return values for variable referenced within the query via $syntax.
	 * @return a map of variables, or an empty map.
	 */
	public Map<String, Object> getVariables() {
		return this.variables;
	}

	/**
	 * Return the locale associated with the request.
	 * @return the locale of {@code null}.
	 */
	@Nullable
	public Locale getLocale() {
		return this.locale;
	}

	/**
	 * Set an {@link ExecutionId} to be used for the {@link ExecutionInput}
	 * @param executionId the execution id to use with the {@link ExecutionInput}.
	 */
	public void executionId(ExecutionId executionId) {
		Assert.notNull(executionId, "executionId should not be null");
		this.executionId = executionId;
	}

	/**
	 * Provide a consumer to configure the {@link ExecutionInput} used for input to
	 * {@link graphql.GraphQL#executeAsync(ExecutionInput)}. The builder is initially
	 * populated with the values from {@link #getQuery()}, {@link #getOperationName()},
	 * and {@link #getVariables()}.
	 * @param configurer a {@code BiFunction} with the current {@code ExecutionInput} and
	 * a builder to modify it.
	 */
	public void configureExecutionInput(BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer) {
		this.executionInputConfigurers.add(configurer);
	}

	/**
	 * Create the {@link ExecutionInput} for request execution. This is initially
	 * populated from {@link #getQuery()}, {@link #getOperationName()}, and
	 * {@link #getVariables()}, and is then further customized through
	 * {@link #configureExecutionInput(BiFunction)}.
	 * @param useRequestId whether the {@link #getId()} should be used as a fallback for {@link ExecutionId}.
	 * @return the execution input
	 */
	public ExecutionInput toExecutionInput(boolean useRequestId) {
		ExecutionInput.Builder inputBuilder = ExecutionInput.newExecutionInput()
				.query(this.query)
				.operationName(this.operationName)
				.variables(this.variables)
				.locale(this.locale);
		if (this.executionId != null) {
			inputBuilder.executionId(this.executionId);
		}
		else if (useRequestId) {
			inputBuilder.executionId(ExecutionId.from(this.id));
		}
		ExecutionInput executionInput = inputBuilder.build();

		for (BiFunction<ExecutionInput, ExecutionInput.Builder, ExecutionInput> configurer : this.executionInputConfigurers) {
			ExecutionInput current = executionInput;
			executionInput = executionInput.transform((builder) -> configurer.apply(current, builder));
		}

		return executionInput;
	}

	/**
	 * Return a Map representation of the request input.
	 * @return map representation of the input
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>(3);
		map.put("query", getQuery());
		if (getOperationName() != null) {
			map.put("operationName", getOperationName());
		}
		if (!CollectionUtils.isEmpty(getVariables())) {
			map.put("variables", new LinkedHashMap<>(getVariables()));
		}
		return map;
	}

	@Override
	public String toString() {
		return "Query='" + getQuery() + "'" +
				((getOperationName() != null) ? ", Operation='" + getOperationName() + "'" : "") +
				(!CollectionUtils.isEmpty(getVariables()) ? ", Variables=" + getVariables() : "") +
				(getLocale() != null ? ", Locale=" + getLocale() : "");
	}

}
