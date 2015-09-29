/*
 * Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.camel.config.xml;

import com.consol.citrus.camel.actions.StopCamelRouteAction;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Bean definition parser for starting Camel routes action in test case.
 * 
 * @author Christoph Deppisch
 * @since 2.4
 */
public class StopCamelRouteActionParser extends AbstractCamelRouteActionParser {

    @Override
    public void parse(BeanDefinitionBuilder beanDefinition, Element element, ParserContext parserContext) {
        List<String> routeIds = new ArrayList<>();
        List<?> routeElements = DomUtils.getChildElementsByTagName(element, "route");
        if (routeElements.size() > 0) {
            for (Iterator<?> iter = routeElements.iterator(); iter.hasNext();) {
                Element routeElement = (Element) iter.next();
                routeIds.add(routeElement.getAttribute("id"));
            }

            beanDefinition.addPropertyValue("routeIds", routeIds);
        }
    }

    @Override
    protected Class<?> getBeanDefinitionClass() {
        return StopCamelRouteAction.class;
    }
}
