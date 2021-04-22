/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.treehouse.schema.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.UNIT

internal val eventType = ClassName("app.cash.treehouse.protocol", "Event")
internal val propertyDiff = ClassName("app.cash.treehouse.protocol", "PropertyDiff")

internal val eventSink = LambdaTypeName.get(receiver = null, eventType, returnType = UNIT)
internal val eventLambda = LambdaTypeName.get(returnType = UNIT).copy(nullable = true)

internal val widget = ClassName("app.cash.treehouse.widget", "Widget")
internal val widgetChildren = widget.nestedClass("Children")
internal val widgetFactory = widget.nestedClass("Factory")

internal val composeNode = ClassName("app.cash.treehouse.compose", "Node")
internal val syntheticChildren = MemberName("app.cash.treehouse.compose", "\$SyntheticChildren")
internal val treehouseScope = ClassName("app.cash.treehouse.compose", "TreehouseScope")

internal val applier = ClassName("androidx.compose.runtime", "Applier")
internal val composable = ClassName("androidx.compose.runtime", "Composable")
internal val composeNodeReference = MemberName("androidx.compose.runtime", "ComposeNode")

internal val composableLambda = LambdaTypeName.get(returnType = UNIT)
  .copy(
    annotations = listOf(
      AnnotationSpec.builder(composable).build(),
    )
  )

internal val iae = ClassName("kotlin", "IllegalArgumentException")
