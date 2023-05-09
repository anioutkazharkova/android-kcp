package com.azharkova.kcp.plugin.domain
import com.azharkova.kcp.plugin.util.createIrBuilder
import com.azharkova.kcp.plugin.util.fqName
import com.azharkova.kcp.plugin.util.irSafeLet
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.callsSuper
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrTypeParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.propertyIfAccessor

val bindViewAnnotation = FqName("com.azharkova.annotations.BindView")
/**
 * @BindView(R.id.some)
 * var someView: View? = getView().findViewById(R.id.some)
 * */

class AndroidTransformExtension (val pluginContext: IrPluginContext, private val messageCollector: MessageCollector) :
IrElementTransformerVoidWithContext() {

    private val irFactory: IrFactory = IrFactoryImpl
    private fun irBuilder(scope: IrSymbol, replacing: IrStatement): IrBuilderWithScope =
        DeclarationIrBuilder(IrGeneratorContextBase(pluginContext.irBuiltIns), scope, replacing.startOffset, replacing.endOffset)

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        return super.visitFunctionNew(declaration)
    }

    override fun visitClassNew(declaration: IrClass): IrStatement {
       if (declaration.functions.any {
            it.annotations.hasAnnotation(bindViewAnnotation)
        }) {
           messageCollector.report(CompilerMessageSeverity.WARNING, declaration.dump())
       }
        return super.visitClassNew(declaration)
    }

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        if (declaration.annotations.hasAnnotation(bindViewAnnotation)) {

           // declaration.backingField = add
            declaration.getter?.let { getter ->
                getter.body = pluginContext.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                    val irBuilder = pluginContext.irBuiltIns.createIrBuilder(
                        getter.symbol,
                        startOffset,
                        endOffset
                    )
                    irBuilder.run {
                        val resultVar = scope.createTmpVariable(
                            irGetField(
                                getter.dispatchReceiverParameter?.let { irGet(it) },
                                declaration.backingField!!
                            )
                        )
                        resultVar.parent = getter
                        statements.add(resultVar)
                    }
                }
            }
        }
        return super.visitPropertyNew(declaration)
    }

  /*  class Transformer(val backendContext: BackendContext):DeclarationTransformer {
       override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
            (declaration as? IrProperty)?.let {
                declaration.backingField = backendContext.buildOrGetNullableField(declaration.backingField!!)
            }
            (declaration as? IrSimpleFunction)?.correspondingPropertySymbol?.owner?.let { property ->
                if (declaration == property.getter) {
                    // f = buildOrGetNullableField is idempotent, i.e. f(f(x)) == f(x)
                    transformGetter(backendContext.buildOrGetNullableField(property.backingField!!), declaration)
                }
            }
            return null
        }


        private fun transformGetter(backingField: IrField, getter: IrFunction) {
            val type = backingField.type
            // assert(!type.isPrimitiveType()) { "'lateinit' modifier is not allowed on primitive types" }
            val startOffset = getter.startOffset
            val endOffset = getter.endOffset
            getter.body = backendContext.irFactory.createBlockBody(startOffset, endOffset) {
                val irBuilder = backendContext.irBuiltIns.createIrBuilder(getter.symbol, startOffset, endOffset)
                irBuilder.run {
                    val resultVar = scope.createTmpVariable(
                        irGetField(getter.dispatchReceiverParameter?.let { irGet(it) }, backingField)
                    )
                    resultVar.parent = getter
                    statements.add(resultVar)
                }
            }
        }

    }*/

    private fun IrBuilderWithScope.irFindViewById(
        receiver: IrExpression, id: IrExpression, container: AndroidContainerType
    ): IrExpression {
        // this[.getView()?|.getContainerView()?].findViewById(R$id.<name>)
        val getView = createMethod(container.fqName.child(Name.identifier("getView")), nullableViewType)
        val findViewByIdParent = if (getView == null) container.fqName else FqName(AndroidConst.VIEW_FQNAME)
        val findViewById = createMethod(findViewByIdParent.child(Name.identifier("findViewById")), nullableViewType) {
            addValueParameter("id", pluginContext.irBuiltIns.intType)
        }
        val findViewCall = irCall(findViewById).apply { putValueArgument(0, id) }
        return if (getView == null) {
            findViewCall.apply { dispatchReceiver = receiver }
        } else {
            irSafeLet(findViewCall.type, irCall(getView).apply { dispatchReceiver = receiver }) { parent ->
                findViewCall.apply { dispatchReceiver = irGet(parent) }
            }
        }
    }
    private fun createPackage(fqName: FqName) =
            IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(pluginContext.moduleDescriptor, fqName)


    private fun getClass(fqName: FqName, isInterface: Boolean = false) =
            irFactory.buildClass {
                name = fqName.shortName()
                kind = if (isInterface) ClassKind.INTERFACE else ClassKind.CLASS
                origin = IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
            }.apply {
                parent = createPackage(fqName.parent())
                createImplicitParameterDeclarationWithWrappedDescriptor()
            }

    private fun createMethod(fqName: FqName, type: IrType, inInterface: Boolean = false, f: IrFunction.() -> Unit = {}): IrSimpleFunction {
        val parent = getClass(fqName = fqName.parent(), isInterface = inInterface)
      return parent.addFunction {
            name = fqName.shortName()
            origin = IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB
            modality = if (inInterface) Modality.ABSTRACT else Modality.FINAL
            returnType = type
        }.apply {
            addDispatchReceiver { this.type = parent.defaultType }
            f()
        }
    }

    private val nullableViewType = getClass(FqName(AndroidConst.VIEW_FQNAME)).defaultType.makeNullable()
}




