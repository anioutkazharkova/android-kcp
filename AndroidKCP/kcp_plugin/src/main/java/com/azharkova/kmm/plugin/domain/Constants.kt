package com.azharkova.kcp.plugin.domain

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.types.isClassWithFqName
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name

fun IrPluginContext.executeFunction(): IrSimpleFunction  = this.referenceClass(ClassIds.COROUTINE_USE_CASE)?.owner?.functions?.filter {
        it.name.asString() == "execute"
    }?.firstOrNull()!!


internal object Names {
    val DEFAULT_COMPANION = Name.identifier("Companion")
    val USECASE_METHOD = Name.identifier("usecase")
    val VIEW_METHOD = Name.identifier("_view")
    val USECASE_IMPL = Name.identifier("\$usecaseImpl")
    val GENERIC_USE_CASE = Name.identifier("com.azharkova.core.GenericUseCase")
    val COROUTINE_USE_CASE = Name.identifier("com.azharkova.core.SuspendUseCase")
    val UNIT = FqName("kotlin.Unit")
    val ANY = FqName("kotlin.Any")

    val REQUEST = "request"
    val EXECUTE = Name.identifier("execute")
    val REPO = Name.identifier("repo")
    val PARAM = Name.identifier("param")
}


internal object ClassIds {
    val UNIT = ClassId(FqName("kotlin"),Name.identifier("Unit"))
    val ANY = ClassId(FqName("kotlin"),Name.identifier("Any"))
    val GENERIC_USE_CASE = ClassId(FqName("com.azharkova.core"), Name.identifier("GenericUseCase"))
    val COROUTINE_USE_CASE = ClassId(FqName("com.azharkova.core"), Name.identifier("SuspendUseCase"))
}

private val IrClassifierSymbol.isFragment: Boolean
    get() = isClassWithFqName(FqNameUnsafe(AndroidConst.FRAGMENT_FQNAME)) ||
            isClassWithFqName(FqNameUnsafe(AndroidConst.SUPPORT_FRAGMENT_FQNAME)) ||
            isClassWithFqName(FqNameUnsafe(AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_FQNAME))