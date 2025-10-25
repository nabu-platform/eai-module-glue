package be.nabu.eai.module.services.glue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.LabelEvaluator;
import be.nabu.glue.core.api.EnclosedLambda;
import be.nabu.glue.core.api.Lambda;
import be.nabu.glue.core.impl.LambdaMethodProvider.LambdaExecutionOperation;
import be.nabu.glue.core.impl.executors.EvaluateExecutor;
import be.nabu.glue.services.CombinedExecutionContext;
import be.nabu.glue.services.CombinedExecutionContextImpl;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.cache.api.Cache;
import be.nabu.libs.cache.api.CacheProvider;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;
import nabu.services.glue.Services;

public class DynamicCacheProvider implements CacheProvider {

	private ExecutionContext context;
	private Services services;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private ScriptRuntime originalRuntime;

	public DynamicCacheProvider(ScriptRuntime originalRuntime, ExecutionContext context) {
		this.originalRuntime = originalRuntime;
		this.context = context;
		this.services = new Services(context);
	}
	
	private CombinedExecutionContext getGlueExecutionContext() {
		ExecutionEnvironment executionEnvironment = new ExecutionEnvironment() {
			@Override
			public Map<String, String> getParameters() {
				HashMap<String, String> map = new HashMap<String, String>();
				map.put(EvaluateExecutor.DEFAULT_VARIABLE_NAME_PARAMETER, "$result");
				return map;
			}
			@Override
			public String getName() {
				return "default";
			}
		};
		CombinedExecutionContext combinedExecutionContext;
		if (context instanceof CombinedExecutionContext) {
			combinedExecutionContext = (CombinedExecutionContext) context;
		}
		else {
			combinedExecutionContext = new CombinedExecutionContextImpl(context, executionEnvironment, new LabelEvaluator() {
				@Override
				public boolean shouldExecute(String label, ExecutionEnvironment environment) {
					return true;
				}
			});
		}
		return combinedExecutionContext;
	}
	
	private static class OverrideRule {
		private String condition;
		private Lambda lambda;
		public String getCondition() {
			return condition;
		}
		public void setCondition(String condition) {
			this.condition = condition;
		}
		public Lambda getLambda() {
			return lambda;
		}
		public void setLambda(Lambda lambda) {
			this.lambda = lambda;
		}
	}
	
	private Map<String, List<OverrideRule>> overrides = new HashMap<>();
	
	private class OverrideCache implements Cache {

		private List<OverrideRule> rules;

		public OverrideCache(List<OverrideRule> rules) {
			this.rules = rules;
		}
		
		@Override
		public boolean put(Object key, Object value) throws IOException {
			// we don't actually keep new caches but we don't tell anyone!
			return true;
		}

		@Override
		public Object get(Object key) throws IOException {
			OverrideRule defaultRule = null;
			OverrideRule chosenRule = null;
			// we must know if your input matches a rule
			for (OverrideRule rule : rules) {
				if (rule.getCondition() == null) {
					defaultRule = rule;
				}
				else {
					try {
						Object evaluate = services.evaluate(rule.getCondition(), List.of(key), false);
						if (evaluate instanceof Boolean && (Boolean) evaluate) {
							chosenRule = rule;
							break;
						}
					}
					catch (Exception e) {
						logger.error("Could not evaluate caching rule: " + rule.getCondition(), e);
						// continue;
					}
				}
			}
			if (chosenRule == null) {
				chosenRule = defaultRule;
			}
			if (chosenRule != null) {
				Lambda lambda = chosenRule.getLambda();
				LambdaExecutionOperation lambdaOperation = new LambdaExecutionOperation(lambda.getDescription(), lambda.getOperation(), 
						lambda instanceof EnclosedLambda ? ((EnclosedLambda) lambda).getEnclosedContext() : new HashMap<String, Object>());
				try {
					ScriptRuntime previousRuntime = ScriptRuntime.getRuntime();
					originalRuntime.registerInThread();
					try {
						Object evaluateWithParameters = lambdaOperation.evaluateWithParameters(getGlueExecutionContext(), key);
						if (evaluateWithParameters != null && !(evaluateWithParameters instanceof ComplexContent)) {
							evaluateWithParameters = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(evaluateWithParameters);
						}
						return evaluateWithParameters;
					}
					finally {
						originalRuntime.unregisterInThread();
						if (previousRuntime != null) {
							previousRuntime.registerInThread();
						}
					}
				}
				catch (EvaluationException e) {
					logger.error("Could not evaluate caching lambda: " + lambda, e);
				}
			}
			return null;
		}

		@Override
		public void clear(Object key) throws IOException {
			// nope
		}

		@Override
		public void clear() throws IOException {
			// nope
		}

		@Override
		public void prune() throws IOException {
			// nope
		}

		@Override
		public void refresh() throws IOException {
			// nope
		}

		@Override
		public void refresh(Object key) throws IOException {
			// nope
		}
		
	}
	
	
	@Override
	public Cache get(String name) throws IOException {
		List<OverrideRule> list = overrides.get(name);
		if (list != null && !list.isEmpty()) {
			return new OverrideCache(list);
		}
		return null;
	}

	@Override
	public void remove(String name) throws IOException {
		overrides.remove(name);
	}

	public void registerOverride(String serviceId, String inputCondition, Lambda overrideLambda) {
		List<OverrideRule> list = overrides.get(serviceId);
		if (list == null) {
			list = new ArrayList<>();
			overrides.put(serviceId, list);
		}
		OverrideRule rule = new OverrideRule();
		rule.setCondition(inputCondition);
		rule.setLambda(overrideLambda);
		list.add(rule);
	}

}
