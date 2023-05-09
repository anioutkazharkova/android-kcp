package com.azharkova.kmm.plugin.domain

import com.azharkova.kcp.plugin.domain.AndroidConst
import com.azharkova.kcp.plugin.domain.AndroidContainerType
import com.azharkova.kcp.plugin.domain.blockBody
import com.azharkova.kcp.plugin.domain.classifierOrNull
import com.azharkova.kcp.plugin.util.*
import com.azharkova.kmm.plugin.util.irGetObject
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.descriptors.toIrBasedDescriptor
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.junit.internal.Classes.getClass
import kotlin.reflect.jvm.internal.impl.descriptors.annotations.AnnotationDescriptor

//import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
//import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
//import org.jetbrains.kotlin.backend.common.lower.callsSuper
//import org.jetbrains.kotlin.descriptors.*
//import org.jetbrains.kotlin.ir.IrStatement
//import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
//import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
//import org.jetbrains.kotlin.ir.builders.*
//import org.jetbrains.kotlin.ir.builders.declarations.*
//import org.jetbrains.kotlin.ir.declarations.*
//import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
//import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
//import org.jetbrains.kotlin.ir.expressions.*
//import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
//import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
//import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
//import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
//import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
//import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
//import org.jetbrains.kotlin.ir.symbols.IrSymbol
//import org.jetbrains.kotlin.ir.symbols.impl.IrTypeParameterSymbolImpl
//import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
//import org.jetbrains.kotlin.ir.types.*
//import org.jetbrains.kotlin.ir.util.*
//import org.jetbrains.kotlin.name.FqName
//import org.jetbrains.kotlin.name.FqNameUnsafe
//import org.jetbrains.kotlin.name.Name
//import org.jetbrains.kotlin.resolve.descriptorUtil.propertyIfAccessor
//import org.jetbrains.kotlin.utils.addToStdlib.safeAs
//import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
//import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
//import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
//import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
//import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver

class AndroidTransformer(val pluginContext: IrPluginContext, private val messageCollector: MessageCollector) :
    IrElementTransformerVoidWithContext() {
    val typeNullableAny = pluginContext.irBuiltIns.anyNType
    val typeUnit = pluginContext.irBuiltIns.unitType

    private val cachedPackages = mutableMapOf<FqName, IrPackageFragment>()
    private val cachedClasses = mutableMapOf<FqName, IrClass>()
    private val cachedMethods = mutableMapOf<FqName, IrSimpleFunction>()
    private val cachedFields = mutableMapOf<FqName, IrField>()

    private val cachedCacheFields = mutableMapOf<IrClass, IrField>()
    private val cachedCacheClearFuns = mutableMapOf<IrClass, IrSimpleFunction>()
    private val cachedCacheLookupFuns = mutableMapOf<IrClass, IrSimpleFunction>()

    val funPrintln =
        pluginContext.referenceFunctions(CallableId(FqName("kotlin.io"), Name.identifier("print")))
            .firstOrNull()?.owner

    override fun visitClassNew(declaration: IrClass): IrStatement {
        // if (declaration.defaultType.classifierOrNull?.isFragment == true) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "cache before")

        val cacheField = declaration.getCacheField()
        declaration.declarations += cacheField // 新增_$_findViewCache屬性
        declaration.declarations += declaration.getClearCacheFun() // 新增_$_clearFindViewByIdCache方法
        declaration.declarations += declaration.getCachedFindViewByIdFun() // 新增_$_findCachedViewById方法
        for (constructor in declaration.constructors) {

          val body = constructor.body as? IrBlockBody ?: continue
            val setCache = irBuilder(constructor.symbol, constructor).run {
                val newCache = irCall(mapFactory, cacheField.type, valueArgumentsCount = 0, typeArgumentsCount = 2).apply {
                    putTypeArgument(0, context.irBuiltIns.intType)
                    putTypeArgument(1, nullableViewType)
                }
                irSetField(irGet(declaration.thisReceiver!!), cacheField, newCache)
            }
            body.statements.add(0, setCache)
        }

        // }
        return super.visitClassNew(declaration)
    }

    private fun IrClass.getClearCacheFun(): IrSimpleFunction =
        cachedCacheClearFuns.getOrPut(this) {
            irFactory.buildFun {
                name = Name.identifier("_\$_clearFindViewByIdCache")
                modality = Modality.OPEN
                returnType = pluginContext.irBuiltIns.unitType
            }.apply {
                val self = addDispatchReceiver { type = defaultType }
                parent = this@getClearCacheFun
                body = irBuilder(symbol, this).irBlockBody {
                    +irCall(mapClear).apply {
                        dispatchReceiver = irGetField(irGet(self), getCacheField())
                    }
                }
            }
        }

    fun IrClass.findViewByIdCached(pluginContext: IrPluginContext): IrSimpleFunction? {
        return functions.find {
            it.isFindViewByIdCached(pluginContext)
        }
    }

    fun IrFunction.isFindViewByIdCached(pluginContext: IrPluginContext): Boolean {
        return name.identifier == FIND_VIEW_BY_ID_CACHED_NAME
    }

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        declaration.isBindable?.let { annotation ->
            var valueData = retrieveParamId(annotation)
            val function =  declaration.parentClassOrNull?.findViewByIdCached(pluginContext)
            declaration.getter?.apply {
                origin = IrDeclarationOrigin.DEFINED
                body = IrBlockBuilder(
                    pluginContext,
                    Scope(symbol),
                    startOffset = startOffset,
                    endOffset = endOffset,
                ).irBlockBody {
                    val self = addDispatchReceiver { type = declaration.parentAsClass.defaultType }
                    +irReturn(irCall(function!!.symbol, declaration.getter!!.returnType).apply {
                        this.putValueArgument(
                            0,
                            irInt(valueData, pluginContext.irBuiltIns.intType)
                        )
                        dispatchReceiver = irGet(self)
                    })
                }
            }
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                  declaration.dump()
            )

        }
        return super.visitPropertyNew(declaration)

    }


    private fun retrieveParamId(annotation: org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor?):Int {
        var valueData = 0
        annotation?.allValueArguments?.forEach { (name, value) ->
            if (name.asString() == "id") {
                valueData = value.toString()?.toIntOrNull() ?: 0
            }
        }
        return valueData
    }

    private fun createField(fqName: FqName, type: IrType) =
        cachedFields.getOrPut(fqName) {
            createClass(fqName.parent()).addField(fqName.shortName(), type, DescriptorVisibilities.PUBLIC)
        }

    private fun IrClass.getCacheField(): IrField =
        cachedCacheFields.getOrPut(this) {
            irFactory.buildField {
                name = Name.identifier("_\$_findViewCache")
                type = pluginContext.irBuiltIns.mutableMapClass.typeWith(
                    pluginContext.irBuiltIns.intType,
                    nullableViewType
                )
            }.apply {
                parent = this@getCacheField
            }
        }

    private fun IrClass.getCachedFindViewByIdFun(): IrSimpleFunction {
      return cachedCacheLookupFuns.getOrPut(this) {
          val containerType = if (this.isFragment()) AndroidContainerType.FRAGMENT else if (this.isActivity()) AndroidContainerType.ACTIVITY else AndroidContainerType.VIEW
          irFactory.buildFun {
              name = Name.identifier(FIND_VIEW_BY_ID_CACHED_NAME)
              modality = Modality.OPEN
              returnType = nullableViewType
          }.apply {
              val self = addDispatchReceiver { type = defaultType }
              val resourceId = addValueParameter("id", pluginContext.irBuiltIns.intType)
              parent = this@getCachedFindViewByIdFun
              body = irBuilder(symbol, this).irBlockBody {
                  val cache = irTemporary(irGetField(irGet(self), getCacheField()))
                  // cache[resourceId] ?: findViewById(resourceId)?.also { cache[resourceId] = it }
                  +irReturn(
                      irElvis(
                          returnType,
                          irCallOp(mapGet.symbol, returnType, irGet(cache), irGet(resourceId))
                      ) {
                          irSafeLet(
                              returnType,
                              irFindViewById(irGet(self), irGet(resourceId), containerType)
                          ) { foundView ->
                              irBlock {
                                  +irCall(mapSet.symbol).apply {
                                      dispatchReceiver = irGet(cache)
                                      putValueArgument(0, irGet(resourceId))
                                      putValueArgument(1, irGet(foundView))
                                  }
                                  +irGet(foundView)
                              }
                          }
                      })
              }
          }
      }
    }


    private val irFactory: IrFactory = IrFactoryImpl

    private fun irBuilder(scope: IrSymbol, replacing: IrStatement): IrBuilderWithScope =
        DeclarationIrBuilder(IrGeneratorContextBase(pluginContext.irBuiltIns), scope, replacing.startOffset, replacing.endOffset)

    private fun createPackage(fqName: FqName) =
        cachedPackages.getOrPut(fqName) {
            IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(pluginContext.moduleDescriptor, fqName)
        }

    private fun createClass(fqName: FqName, isInterface: Boolean = false) =
        cachedClasses.getOrPut(fqName) {
            irFactory.buildClass {
                name = fqName.shortName()
                kind = if (isInterface) ClassKind.INTERFACE else ClassKind.CLASS
                origin = IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
            }.apply {
                parent = createPackage(fqName.parent())
                createImplicitParameterDeclarationWithWrappedDescriptor()
            }
        }

    private fun createMethod(fqName: FqName, type: IrType, inInterface: Boolean = false, f: IrFunction.() -> Unit = {}) =
        cachedMethods.getOrPut(fqName) {
            val parent = createClass(fqName.parent(), inInterface)
            parent.addFunction {
                name = fqName.shortName()
                origin = IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
                modality = if (inInterface) Modality.ABSTRACT else Modality.FINAL
                returnType = type
            }.apply {
                addDispatchReceiver { this.type = parent.defaultType }
                f()
            }
        }

    // NOTE: sparse array version intentionally not implemented; this plugin is deprecated
    @OptIn(FirIncompatiblePluginAPI::class)
    private val mapFactory = pluginContext.referenceFunctions(FqName("kotlin.collections.mutableMapOf"))
        .single { it.owner.valueParameters.isEmpty() }
    private val mapGet = pluginContext.irBuiltIns.mapClass.owner.functions
        .single { it.name.asString() == "get" && it.valueParameters.size == 1 }
    private val mapSet = pluginContext.irBuiltIns.mutableMapClass.owner.functions
        .single { it.name.asString() == "put" && it.valueParameters.size == 2 }
    private val mapClear = pluginContext.irBuiltIns.mutableMapClass.owner.functions
        .single { it.name.asString() == "clear" && it.valueParameters.isEmpty() }

    private val nullableViewType = createClass(FqName(AndroidConst.VIEW_FQNAME)).defaultType.makeNullable()



    private fun IrBuilderWithScope.irFindViewById(
        receiver: IrExpression, id: IrExpression, container: AndroidContainerType = AndroidContainerType.FRAGMENT
    ): IrExpression {
        val getView = when (container) {
            AndroidContainerType.FRAGMENT,
            AndroidContainerType.ANDROIDX_SUPPORT_FRAGMENT,
            AndroidContainerType.SUPPORT_FRAGMENT -> createMethod(container.fqName.child("getView"), nullableViewType)
           else -> null
        }
        // this[.getView()?|.getContainerView()?].findViewById(R$id.<name>)

        val findViewByIdParent = if (getView == null) container.fqName else FqName(AndroidConst.VIEW_FQNAME)
        val findViewById = createMethod(findViewByIdParent.child("findViewById"), nullableViewType) {
            addValueParameter("id", pluginContext.irBuiltIns.intType)
        }
        val findViewCall = irCall(findViewById.symbol).apply { putValueArgument(0, id) }
        return if (getView == null) {
            findViewCall.apply { dispatchReceiver = receiver }
        } else {

            irSafeLet(findViewCall.type, irCall(getView.symbol).apply { dispatchReceiver = receiver }) { parent ->
                findViewCall.apply { dispatchReceiver = irGet(parent) }
            }
        }
    }
}


private fun FqName.child(name: String) = child(Name.identifier(name))


private inline fun IrBuilderWithScope.irSafeCall(
    type: IrType, lhs: IrExpression,
    ifNull: IrBuilderWithScope.() -> IrExpression,
    ifNotNull: IrBuilderWithScope.(IrVariable) -> IrExpression
) = irBlock(origin = IrStatementOrigin.SAFE_CALL) {
    +irTemporary(lhs).let { irIfNull(type, irGet(it), ifNull(), ifNotNull(it)) }
}

private inline fun IrBuilderWithScope.irSafeLet(type: IrType, lhs: IrExpression, rhs: IrBuilderWithScope.(IrVariable) -> IrExpression) =
    irSafeCall(type, lhs, { irNull() }, rhs)

private inline fun IrBuilderWithScope.irElvis(type: IrType, lhs: IrExpression, rhs: IrBuilderWithScope.() -> IrExpression) =
    irSafeCall(type, lhs, rhs) { irGet(it) }

private val AndroidContainerType.fqName: FqName
    get() = FqName(internalClassName.replace("/", "."))
