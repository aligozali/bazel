// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.analysis.skylark.annotations.processor;

import com.google.devtools.build.lib.analysis.skylark.annotations.SkylarkConfigurationField;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Annotation processor for {@link SkylarkConfigurationField}.
 *
 * <p>Checks the following invariants about {@link SkylarkConfigurationField}-annotated methods:
 * <ul>
 * <li>The annotated method must be on a configuration fragment exposed to skylark.</li>
 * <li>The method must have return type Label.</li>
 * <li>The method must be public.</li>
 * <li>The method must have zero arguments.</li>
 * <li>The method must not throw exceptions.</li>
 * </ul>
 *
 * <p>These properties can be relied upon at runtime without additional checks.
 */
@SupportedAnnotationTypes({"com.google.devtools.build.lib.analysis.skylark.annotations.SkylarkConfigurationField"})
public final class SkylarkConfigurationFieldProcessor extends AbstractProcessor {

  private Messager messager;
  private Types typeUtils;
  private TypeMirror labelType;
  private TypeMirror configurationFragmentType;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    typeUtils = processingEnv.getTypeUtils();
    Elements elementUtils = processingEnv.getElementUtils();
    labelType =
        elementUtils.getTypeElement("com.google.devtools.build.lib.cmdline.Label").asType();
    configurationFragmentType =
        elementUtils.getTypeElement(
            "com.google.devtools.build.lib.analysis.config.BuildConfiguration.Fragment").asType();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(SkylarkConfigurationField.class)) {
      // Only methods are annotated with SkylarkConfigurationField. This is verified by the
      // @Target(ElementType.METHOD) annotation.
      ExecutableElement methodElement = (ExecutableElement) element;

      if (!isMethodOfSkylarkExposedConfigurationFragment(methodElement)) {
        error(methodElement, "@SkylarkConfigurationField annotated methods must be methods "
            + "of configuration fragments with the @SkylarkModule annotation.");
      }
      if (!typeUtils.isSameType(methodElement.getReturnType(), labelType)) {
        error(methodElement, "@SkylarkConfigurationField annotated methods must return Label.");
      }
      if (!methodElement.getModifiers().contains(Modifier.PUBLIC)) {
        error(methodElement, "@SkylarkConfigurationField annotated methods must be public.");
      }
      if (!methodElement.getParameters().isEmpty()) {
        error(methodElement,
            "@SkylarkConfigurationField annotated methods must have zero arguments.");
      }
      if (!methodElement.getThrownTypes().isEmpty()) {
        error(methodElement,
            "@SkylarkConfigurationField annotated must not throw exceptions.");
      }
    }
    return true;
  }

  private boolean isMethodOfSkylarkExposedConfigurationFragment(
      ExecutableElement methodElement) {
    if (methodElement.getEnclosingElement().getKind() != ElementKind.CLASS) {
      return false;
    }
    Element classElement = methodElement.getEnclosingElement();
    if (!typeUtils.isAssignable(classElement.asType(), configurationFragmentType)) {
      return false;
    }
    if (classElement.getAnnotation(SkylarkModule.class) == null) {
      return false;
    }
    return true;
  }

  /**
   * Prints an error message & fails the compilation.
   *
   * @param e The element which has caused the error. Can be null
   * @param msg The error message
   */
  public void error(Element e, String msg) {
    messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
