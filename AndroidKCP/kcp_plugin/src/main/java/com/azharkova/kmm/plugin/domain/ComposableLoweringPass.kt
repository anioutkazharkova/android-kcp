package com.azharkova.kmm.plugin.domain

import com.azharkova.kcp.plugin.domain.Names
import com.azharkova.kcp.plugin.domain.blockBody
import com.azharkova.kcp.plugin.domain.irType
import com.azharkova.kcp.plugin.util.*
import com.azharkova.kcp.plugin.util.SYNTHETIC_OFFSET
import com.azharkova.kmm.plugin.util.irGetObject
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.descriptors.toIrBasedDescriptor
import org.jetbrains.kotlin.ir.expressions.IrCallableReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSpreadElementImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ComposableLoweringPass (private val pluginContext: IrPluginContext, private val messageCollector: MessageCollector) :
    ClassLoweringPass {
    private val irFactory = pluginContext.irFactory
    private val irBuiltIns = pluginContext.irBuiltIns

    val androidFun = pluginContext.referenceFunctions(CallableId(FqName("androidx.compose.ui.viewinterop"), Name.identifier("AndroidView")))
        .firstOrNull()?.owner

    val modifierObject = pluginContext.referenceClass(ClassId(FqName("androidx.compose.ui"), Name.identifier("Modifier")))



    @OptIn(FirIncompatiblePluginAPI::class, ObsoleteDescriptorBasedAPI::class)
    override fun lower(irClass: IrClass) {


        if (!irClass.toIrBasedDescriptor().isToComposable()) {
            return
        }
        val function = irClass.functions.firstOrNull { it.name.asString() == "viewComposable" }
        val factoryFun = irClass.viewFactoryFun(irClass)
        factoryFun.parent = irClass
        function?.body = pluginContext.blockBody(function!!.symbol) {

            val factoryFun = irClass.viewFactoryFun(irClass)
            factoryFun.parent = irClass


            val lambdaT = irFactory.buildFun {
                name = Name.special("<anonymous>")
                this.returnType = pluginContext.irBuiltIns.unitType
                this.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                this.visibility = DescriptorVisibilities.LOCAL
            }.also { fn ->
                fn.parent = function
                val localIrBuilder = DeclarationIrBuilder(pluginContext, fn.symbol)
                localIrBuilder.irBlockBody {
                    // Call the function again with the same parameters
                    +irReturn(
                        irCallConstructor(
                            irClass.primaryConstructor!!.symbol,
                            emptyList()
                        ).apply {
                            symbol.owner
                                .valueParameters
                                .forEachIndexed { index, param ->
                                    if (param.isVararg) {
                                        putValueArgument(
                                            index,
                                            IrVarargImpl(
                                                UNDEFINED_OFFSET,
                                                UNDEFINED_OFFSET,
                                                param.type,
                                                param.varargElementType!!,
                                                elements = listOf(
                                                    IrSpreadElementImpl(
                                                        UNDEFINED_OFFSET,
                                                        UNDEFINED_OFFSET,
                                                        irGet(param)
                                                    )
                                                )
                                            )
                                        )
                                    } else {
                                        // NOTE(lmr): should we be using the parameter here, or the temporary
                                        // with the default value?
                                        putValueArgument(index, irGet(param))
                                    }
                                }

                            // new composer
                            putValueArgument(
                                0,
                                irGet(factoryFun.valueParameters[0])
                            )
                            //extensionReceiver = function.extensionReceiverParameter?.let { irGet(it) }
                            //  dispatchReceiver =
                            // irGet(addDispatchReceiver { type = irClass.defaultType })
                        })

                }
            }



            //
          /*  +irReturn(irCall(androidFun!!.symbol).also {
                it.putValueArgument(0, lambdaArgument(lambdaT))
                it.putValueArgument(1, irGetObject(modifierObject!!))
            })*/
        }

       /* val function = irClass.addFunction {
            name = Name.identifier("viewComposable")
            visibility = DescriptorVisibilities.PRIVATE
            returnType = irBuiltIns.unitType
        }.apply {
            val function = this
            this.parent = irClass
            this.dispatchReceiverParameter = irClass.thisReceiver?.copyTo(this)
            this.body = pluginContext.blockBody(this.symbol) {
                /*  +irReturn(irCall(androidFun!!.symbol).also {

                })*/

            }
        }
        function.symbol.owner.annotations += createComposableAnnotation()*/
        irClass.generateFactory(irClass.companionObject() as IrClass, function)
        messageCollector.report(CompilerMessageSeverity.WARNING, irClass.dump())
    }
    val funPrintln =
        pluginContext.referenceFunctions(CallableId(FqName("kotlin.io"), Name.identifier("print")))
            .firstOrNull()?.owner

    private fun IrClass.generateFactory(companionClass: IrClass, callableFunction: IrFunction) {
        val parentClass = this
        val irClass = parentClass
        var function = companionClass.functions.firstOrNull() { it.name == Names.VIEW_METHOD }
        function?.returnType = irBuiltIns.unitType



        function?.let {

            //function?.parent = companionClass
            function?.dispatchReceiverParameter = companionClass.thisReceiver?.copyTo(function)
            function.symbol.owner.annotations += createComposableAnnotation()

            function?.body = pluginContext.blockBody(function.symbol) {

                if (function.toIrBasedDescriptor().annotations.hasAnnotation(FqName("androidx.compose.runtime.Composable"))) {
                    messageCollector.report(CompilerMessageSeverity.WARNING, "Composable")
                } else {
                    messageCollector.report(CompilerMessageSeverity.WARNING, "no Composable")
                }
            }

           /* function?.body = pluginContext.blockBody(function.symbol) {

                if (function.toIrBasedDescriptor().annotations.hasAnnotation(FqName("androidx.compose.runtime.Composable"))) {
                    messageCollector.report(CompilerMessageSeverity.WARNING, "Composable")
                } else {
                    messageCollector.report(CompilerMessageSeverity.WARNING, "no Composable")
                }
                val factoryFun = irClass.viewFactoryFun(irClass)
                factoryFun.parent = irClass
//factoryFun.parent = irClass


                val lambdaT = irFactory.buildFun {
                    name = Name.special("<anonymous>")
                    this.returnType = pluginContext.irBuiltIns.unitType
                    this.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                    this.visibility = DescriptorVisibilities.LOCAL
                }.also { fn ->
                    fn.parent = function
                    val localIrBuilder = DeclarationIrBuilder(pluginContext, fn.symbol)
                    localIrBuilder.irBlockBody {
                        // Call the function again with the same parameters
                        +irReturn(
                            irCallConstructor(
                                irClass.primaryConstructor!!.symbol,
                                emptyList()
                            ).apply {
                                symbol.owner
                                    .valueParameters
                                    .forEachIndexed { index, param ->
                                        if (param.isVararg) {
                                            putValueArgument(
                                                index,
                                                IrVarargImpl(
                                                    UNDEFINED_OFFSET,
                                                    UNDEFINED_OFFSET,
                                                    param.type,
                                                    param.varargElementType!!,
                                                    elements = listOf(
                                                        IrSpreadElementImpl(
                                                            UNDEFINED_OFFSET,
                                                            UNDEFINED_OFFSET,
                                                            irGet(param)
                                                        )
                                                    )
                                                )
                                            )
                                        } else {
                                            // NOTE(lmr): should we be using the parameter here, or the temporary
                                            // with the default value?
                                            putValueArgument(index, irGet(param))
                                        }
                                    }

                                // new composer
                                putValueArgument(
                                    0,
                                    irGet(factoryFun.valueParameters[0])
                                )
                                //extensionReceiver = function.extensionReceiverParameter?.let { irGet(it) }
                             //  dispatchReceiver =
                                  // irGet(addDispatchReceiver { type = irClass.defaultType })
                            })

                    }
                }

                // val localIrBuilder = DeclarationIrBuilder(context, factoryFun.symbol)
                val lambda = pluginContext.buildLambda(irClass.defaultType) {
                    +irReturn(
                        irCallConstructor(
                            irClass.primaryConstructor!!.symbol!!,
                            emptyList()
                        ).apply {
                            symbol.owner
                                .valueParameters
                                .forEachIndexed { index, param ->
                                    if (param.isVararg) {
                                        putValueArgument(
                                            index,
                                            IrVarargImpl(
                                                UNDEFINED_OFFSET,
                                                UNDEFINED_OFFSET,
                                                param.type,
                                                param.varargElementType!!,
                                                elements = listOf(
                                                    IrSpreadElementImpl(
                                                        UNDEFINED_OFFSET,
                                                        UNDEFINED_OFFSET,
                                                        irGet(param)
                                                    )
                                                )
                                            )
                                        )
                                    } else {
                                        // NOTE(lmr): should we be using the parameter here,
                                        // with the default value?
                                        putValueArgument(index, irGet(param))
                                    }
                                }
                          //  dispatchReceiver =
                            //    irGet(addDispatchReceiver { type = irClass.defaultType })

                        })

                }


                //
                +irReturn(irCall(androidFun!!.symbol).also {
                    it.putValueArgument(0, lambdaArgument(lambdaT))
                   /* it.putValueArgument(0, lambdaArgument(pluginContext.buildLambda(irClass.defaultType){
                        +irReturn(irCallConstructor(irClass.primaryConstructor!!.symbol,
                            emptyList()
                        ).also { call ->
                            irClass.primaryConstructor?.valueParameters.orEmpty().forEachIndexed { index, param ->
                                if (param.isVararg) {
                                    call.putValueArgument(
                                        index,
                                        IrVarargImpl(
                                            UNDEFINED_OFFSET,
                                            UNDEFINED_OFFSET,
                                            param.type,
                                            param.varargElementType!!,
                                            elements = listOf(
                                                IrSpreadElementImpl(
                                                    UNDEFINED_OFFSET,
                                                    UNDEFINED_OFFSET,
                                                    irGet(param)
                                                )
                                            )
                                        ))
                                }else {
                                    call.putValueArgument(index, irGet(param))
                                }
                            }
                            call.putValueArgument(
                                0,
                                irGet(factoryFun.valueParameters[0])
                            )
                        })
                    }))*/
                    // it.dispatchReceiver = irGet(self)

                    /*it.putValueArgument(
                    0,
                    lambdaArgument(pluginContext.buildLambda(irClass.defaultType, funApply = {
                        +irReturn(irCallConstructor(irClass.primaryConstructor!!.symbol,
                            emptyList()
                        ).also { call ->
                           irClass.primaryConstructor?.valueParameters.orEmpty().forEachIndexed { index, param ->
                                if (param.isVararg) {
                                    call.putValueArgument(
                                        index,
                                        IrVarargImpl(
                                            UNDEFINED_OFFSET,
                                            UNDEFINED_OFFSET,
                                            param.type,
                                            param.varargElementType!!,
                                            elements = listOf(
                                                IrSpreadElementImpl(
                                                    UNDEFINED_OFFSET,
                                                    UNDEFINED_OFFSET,
                                                    irGet(param)
                                                )
                                            )
                                        ))
                                }else {
                                        call.putValueArgument(index, irGet(param))
                                    }
                            }
                        })
                    }))
                )*/
                    it.putValueArgument(1, irGetObject(modifierObject!!))
                })
            }*/
        }
    }

    inline fun IrPluginContext.buildLambda(
        returnType: IrType,
        funApply: IrBlockBodyBuilder.() -> Unit
    ): IrSimpleFunction = irFactory.buildFun {
        name = Name.special("<anonymous>")
        this.returnType = returnType
        this.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        this.visibility = DescriptorVisibilities.LOCAL
    }.apply {
        body = DeclarationIrBuilder(this@buildLambda, symbol).irBlockBody(body = funApply).apply {

        }
    }

    fun lambdaArgument(
        lambda: IrSimpleFunction,
        type: IrType = run {
            val base = if (lambda.isSuspend)
                pluginContext.irBuiltIns.suspendFunctionN(lambda.allParameters.size)
            else
                pluginContext.irBuiltIns.functionN(lambda.allParameters.size)

            base.typeWith(lambda.allParameters.map { it.type } + lambda.returnType)
        }
    ) = IrFunctionExpressionImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        type,
        lambda,
        IrStatementOrigin.LAMBDA
    )


    private fun IrClass.viewFactoryFun(viewtype: IrClass):IrSimpleFunction {
        val function = irFactory.buildFun{
            name = Name.identifier("viewFactory")
            returnType = viewtype.defaultType

        }.apply {
           dispatchReceiverParameter = this@viewFactoryFun.thisReceiver?.copyTo(this)
            val context = addValueParameter {
                name = Name.identifier("context")
                type = pluginContext.irType(ClassId(FqName("android.content"), Name.identifier("Context")))
            }
            body = DeclarationIrBuilder(pluginContext, this.symbol).irBlockBody {
                +irReturn(irCallConstructor(
                    viewtype.primaryConstructor!!.symbol,
                    emptyList()
                ).also {
                    it.dispatchReceiver = irGet(addDispatchReceiver { type = viewtype.defaultType })
                    it.putValueArgument(0, irGet(context))
                })
            }
        }
        return function
    }

    private val composableIrClass =
        pluginContext.referenceClass(ComposeClassIds.Composable)!!.owner

    private fun createComposableAnnotation() =
        IrConstructorCallImpl(
            startOffset = SYNTHETIC_OFFSET,
            endOffset = SYNTHETIC_OFFSET,
            type = composableIrClass.defaultType,
            symbol = composableIrClass.primaryConstructor!!.symbol,
            typeArgumentsCount = 0,
            constructorTypeArgumentsCount = 0,
            valueArgumentsCount = 0
        )
}