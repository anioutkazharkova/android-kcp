package com.azharkova.kmm.plugin.util

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.util.defaultType


fun IrBuilderWithScope.irGetObject(irObject: IrClass) =
    IrGetObjectValueImpl(
        startOffset,
        endOffset,
        irObject.defaultType,
        irObject.symbol
    )

