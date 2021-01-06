/*
 * Copyright (C) 2018  niaoge<78493244@qq.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.stategen.framework.spring.mvc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.stategen.framework.annotation.Wrap;
import org.stategen.framework.lite.IResponseStatus;
import org.stategen.framework.response.ResponseStatusTypeHandler;
import org.stategen.framework.response.ResponseUtil;
import org.stategen.framework.util.AnnotationUtil;
import org.stategen.framework.util.CollectionUtil;

/**
 * 该类将返回结果包装成response.
 */
@ControllerAdvice(annotations = { Controller.class, RestController.class })
public class ResponseBodyAdviceWrapper extends ResponseStatusTypeHandler implements ResponseBodyAdvice<Object>, InitializingBean {
    final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ResponseBodyAdviceWrapper.class);
    private static Map<Method, Boolean> needWrapMethodsCache = new ConcurrentHashMap<Method, Boolean>();

    //如果不设置，会把所有controller都包装，显示一些jar包中的controller有自己的返回值
    public static Set<String> packages = null;

    public static Set<Class<? extends Annotation>> annotations = null;


    private Boolean wrapperResponse = true;

    public void setWrapperResponse(Boolean wrapperResponse) {
        if (wrapperResponse != null) {
            this.wrapperResponse = wrapperResponse;
        }
    }

    protected static boolean checkMethodPath(Method method) {
        if (CollectionUtil.isNotEmpty(packages)) {
            Class<?> declaringClass = method.getDeclaringClass();
            Package pkg = declaringClass.getPackage();
            String packageName = pkg.getName();
            if (packages.contains(packageName)) {
                return true;
            }
        }

        if (CollectionUtil.isNotEmpty(annotations)) {
            for (Class<? extends Annotation> annClz : annotations) {
                Annotation anno = AnnotationUtil.getMethodOrOwnerAnnotation(method, annClz);
                if (anno != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> converterType) {
        final Method method = methodParameter.getMethod();
        return supportMethod(method);
    }

    public static boolean supportMethod(Method method) {
        Boolean needWrapFlag;
        needWrapFlag = needWrapMethodsCache.get(method);
        if (needWrapFlag != null) {
            return needWrapFlag;
        }

        needWrapFlag = false;
        try {
            if (!checkMethodPath(method)) {
                return needWrapFlag;
            }
            //不是 RequestMapping的方法不额外处理
            RequestMapping requestMappingAnn = AnnotationUtil.getAnnotation(method, RequestMapping.class);
            if (requestMappingAnn == null) {
                return needWrapFlag;
            }

            ResponseBody responseBodyAnno = AnnotationUtil.getMethodOrOwnerAnnotation(method, ResponseBody.class);
            if (responseBodyAnno == null) {
                return needWrapFlag;
            }
            
           
            Wrap wrapAnno = AnnotationUtil.getMethodOrOwnerAnnotation(method, Wrap.class);
            if (wrapAnno==null){
                return needWrapFlag;
            }
            
            boolean exclude = wrapAnno.value() == false;
            if (exclude){
                return needWrapFlag;
            }
            
            needWrapFlag = true;
            return needWrapFlag;
        } finally {
            needWrapMethodsCache.put(method, needWrapFlag);
        }
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter methodParameter, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        Object data = body;
        //如果是状态，直接返回
        if (data instanceof HttpStatus) {
            HttpStatus httpStatus = (HttpStatus) data;
            response.setStatusCode(httpStatus);
            return null;
        }

        if (this.wrapperResponse) {
            //如果是IResponseStatus，作为状态返回
            if (data instanceof IResponseStatus) {
                IResponseStatus responseStatus = (IResponseStatus) data;
                data = ResponseUtil.buildResponse(null, responseStatus);
            } else {
                data = ResponseUtil.buildResponse(data, getResponseStatus());
            }
        }
        
        if (response instanceof ServletServerHttpResponse) {
            HttpHeaders headers = ((ServletServerHttpResponse) response).getHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return data;
    }

    public void setPackages(Set<String> packages) {
        ResponseBodyAdviceWrapper.packages = packages;
    }

    public void setAnnotations(Set<Class<? extends Annotation>> annotations) {
        ResponseBodyAdviceWrapper.annotations = annotations;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
//        WebApplicationContext webApplicationContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);
//        Map<String, ResponseBodyAdviceWrapper> beansOfType = webApplicationContext.getBeansOfType(ResponseBodyAdviceWrapper.class);
//        if (CollectionUtil.isNotEmpty(beansOfType)) {
//            throw new Exception("ResponseBodyAdviceWrapper can not been defined than once!");
//        }

        if (CollectionUtil.isEmpty(packages) && CollectionUtil.isEmpty(annotations)) {
            throw new Exception("packages or annotations must been set at least!");
        }

    }

    public <T extends Enum<T> & IResponseStatus> void setResponseStatusOfResult(T responseStatusOfResult) {
        super.setResponseStatus(responseStatusOfResult);
    }

}
