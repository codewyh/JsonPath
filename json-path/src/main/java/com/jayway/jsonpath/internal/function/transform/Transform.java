package com.jayway.jsonpath.internal.function.transform;

import com.googlecode.aviator.AviatorEvaluator;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.internal.EvaluationContext;
import com.jayway.jsonpath.internal.PathRef;
import com.jayway.jsonpath.internal.function.ParamType;
import com.jayway.jsonpath.internal.function.Parameter;
import com.jayway.jsonpath.internal.function.PathFunction;
import com.jayway.jsonpath.spi.json.JsonProvider;

import java.util.*;

/**
 * @author weiyuhao
 * @Date 2019/12/19 17:43
 * @Description
 */
public class Transform implements PathFunction {

    @Override
    public Object invoke(String currentPath, PathRef parent, Object model, EvaluationContext ctx, List<Parameter> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return model;
        }

        for (Parameter parameter : parameters) {
            if (!ParamType.JSON.equals(parameter.getType())) {
                throw new InvalidPathException("expected json parameter in transform function");
            }
        }

        JsonProvider jsonProvider = ctx.configuration().jsonProvider();

        if (jsonProvider.isMap(model)) {
            return transformObject(model, parameters, jsonProvider);
        } else if (jsonProvider.isArray(model)) {
            Iterable<?> objects = jsonProvider.toIterable(model);
            Object array = jsonProvider.createArray();
            int idx = 0;
            for (Object object : objects) {
                Object jsonObject = transformObject(object, parameters, jsonProvider);
                jsonProvider.setArrayIndex(array, idx, jsonObject);
                ++idx;
            }
            return array;
        } else {
            return model;
        }
    }

    private Object transformObject(Object model, List<Parameter> parameters, JsonProvider jsonProvider) {
        Object jsonObject = jsonProvider.createMap();
        for (Parameter parameter : parameters) {
            Object json = jsonProvider.parse(parameter.getJson());
            Collection<String> properties = jsonProvider.getPropertyKeys(json);
            for (String property : properties) {
                Object propertyCfg = jsonProvider.getMapValue(json, property);

                Map<String, Object> ctx = new HashMap<>();
                int size = jsonProvider.length(propertyCfg);
                for (int i = 0; i < size; i++) {
                    String expr = (String) jsonProvider.getArrayIndex(propertyCfg, i);
                    Object result = calNewField(model, expr, ctx, i);
                    if (i + 1 == size) {
                        jsonProvider.setProperty(jsonObject, property, result);
                    }

                }
            }
        }

        return jsonObject;
    }

    private Object eval(ExprTypeEnum exprTypeEnum, Object model, String expr, Map<String, Object> ctx) {
        Object result = null;
        switch (exprTypeEnum) {
            case AVIATOR:
                result = AviatorEvaluator.execute(expr, ctx);
                break;
            case JSON_PATH:
                result = JsonPath.read(model, expr);
                break;
            default:
                break;
        }

        return result;
    }

    private Object cal(ExprTypeEnum exprTypeEnum, String varName, Object model, String expr,
                       Map<String, Object> ctx, int index) {
        if (exprTypeEnum == null) {
            return null;
        }
        Object result = eval(exprTypeEnum, model, expr, ctx);
        if (varName != null) {
            ctx.put(varName, result);
        }
        ctx.put("$d" + index, result);


        return result;
    }

    private Object calNewField(Object model, String expr, Map<String, Object> ctx, int index) {
        String[] parts = expr.split("->");

        ExprTypeEnum exprTypeEnum = null;
        String varName = null;
        String evalExpr = null;
        if (parts.length == 2) {
            exprTypeEnum = ExprTypeEnum.typeOf(parts[0]);
            evalExpr = parts[1];
        } else if (parts.length == 3) {
            exprTypeEnum = ExprTypeEnum.typeOf(parts[0]);
            varName = parts[1];
            evalExpr = parts[2];
        }

        return cal(exprTypeEnum, varName, model, evalExpr, ctx, index);
    }

    enum ExprTypeEnum {
        JSON_PATH(new String[]{"p", "P"}),
        AVIATOR(new String[]{"a", "A"});
        String type;
        Set<String> types;

        ExprTypeEnum(String[] types) {
            this.types = new HashSet<>(Arrays.asList(types));
        }

        static ExprTypeEnum typeOf(String type) {
            for (ExprTypeEnum value : ExprTypeEnum.values()) {
                if (value.type.equals(type)) {
                    return value;
                }
            }
            return null;
        }
    }
}
