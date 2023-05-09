package com.azharkova.kcp.plugin.util


import com.azharkova.kcp.plugin.domain.AndroidConst
import com.azharkova.kcp.plugin.domain.AndroidContainerType
import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.getOrPut
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlock
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping.mapping
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.IrBasedClassDescriptor
import org.jetbrains.kotlin.ir.descriptors.toIrBasedDescriptor
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.SetDeclarationsParentVisitor
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.*

val usecaseName = FqName("com.azharkova.annotations.GenUseCase")
val composableName = FqName("com.azharkova.annotations.ToComposable")

fun coroutineUsecase(paramIn: String = "Kotlin.Unit", paramOut: String = "Kotlin.Unit") = FqName("com.azharkova.core.SuspendUseCase<${paramIn}, ${paramOut}>")

fun ClassDescriptor.isUseCase(): Boolean =
    annotations.hasAnnotation(usecaseName)

fun ClassDescriptor.isToComposable(): Boolean = annotations.hasAnnotation(composableName)

const val SYNTHETIC_OFFSET = -2

fun IrClass.toIrBasedDescriptor() = IrBasedClassDescriptor(this)

fun IrBuilderWithScope.irString(builderAction: StringBuilder.() -> Unit) =
    irString(buildString { builderAction() })




fun IrBuilderWithScope.irLambda(
    returnType: IrType,
    lambdaType: IrType,
    startOffset: Int = this.startOffset,
    endOffset: Int = this.endOffset,
    block: IrBlockBodyBuilder.() -> Unit
): IrFunctionExpression {
    val scope = this
    val lambda = context.irFactory.buildFun {
        name = Name.special("<anonymous>")
        this.returnType = returnType
        visibility = DescriptorVisibilities.LOCAL
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    }.apply {
        val bodyBuilder = DeclarationIrBuilder(context, symbol)
        body = bodyBuilder.irBlockBody {
            block()
        }
        parent = scope.parent
    }
    return IrFunctionExpressionImpl(startOffset, endOffset, lambdaType, lambda, IrStatementOrigin.LAMBDA)
}

fun IrFunction.setBody(context: IrPluginContext, body: IrBlockBodyBuilder.() -> Unit): IrBlockBody =
    DeclarationIrBuilder(context, symbol)
        .irBlockBody(body = body)
        .also { this.body = it }

fun IrConstructor.toIrConstructorCall(): IrConstructorCall =
    IrConstructorCallImpl.fromSymbolOwner(
        type = returnType,
        constructorSymbol = symbol
    )

fun IrType.asIrSimpleType(): IrSimpleType =
    this as IrSimpleType

/* Copied from K/N */
fun IrDeclarationContainer.addChild(declaration: IrDeclaration) {
    this.declarations += declaration
    declaration.setDeclarationsParent(this)
}



fun <T : IrElement> T.setDeclarationsParent(parent: IrDeclarationParent): T {
    accept(SetDeclarationsParentVisitor, parent)
    return this
}

fun IrConstructorCall.getValueArgument(name: Name): IrExpression? {
    val index = symbol.owner.valueParameters.find { it.name == name }?.index ?: return null
    return getValueArgument(index)
}


fun FqName.child(name: String) = child(Name.identifier(name))

fun IrSimpleFunction.callWithRanges(source: IrExpression) =
    IrCallImpl.fromSymbolOwner(source.startOffset, source.endOffset, returnType, symbol)

 inline fun IrBuilderWithScope.irSafeCall(
    type: IrType, lhs: IrExpression,
    ifNull: IrBuilderWithScope.() -> IrExpression,
    ifNotNull: IrBuilderWithScope.(IrVariable) -> IrExpression
) = irBlock(origin = IrStatementOrigin.SAFE_CALL) {
    +irTemporary(lhs).let { irIfNull(type, irGet(it), ifNull(), ifNotNull(it)) }
}

inline fun IrBuilderWithScope.irSafeLet(type: IrType, lhs: IrExpression, rhs: IrBuilderWithScope.(IrVariable) -> IrExpression) =
    irSafeCall(type, lhs, { irNull() }, rhs)

inline fun IrBuilderWithScope.irElvis(type: IrType, lhs: IrExpression, rhs: IrBuilderWithScope.() -> IrExpression) =
    irSafeCall(type, lhs, rhs) { irGet(it) }

 val AndroidContainerType.fqName: FqName
    get() = FqName(className.replace("/", "."))



fun IrBuiltIns.createIrBuilder(
    symbol: IrSymbol,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET
) =
    DeclarationIrBuilder(IrGeneratorContextBase(this), symbol, startOffset, endOffset)

fun BackendContext.createIrBuilder(
    symbol: IrSymbol,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET
) =
    irBuiltIns.createIrBuilder(symbol, startOffset, endOffset)
val bindViewAnnotation = FqName("com.azharkova.annotations.BindView")



val IMPLICIT_ANDROID_EXTENSIONS_TYPES = setOf(
    "android.app.Activity",
    "androidx.fragment.app.Fragment"
)

const val FIND_VIEW_BY_ID_CACHED_NAME = "findViewByIdCached"

const val DELEGATE_FIELD_NAME = "\$\$androidExtensionsImpl"

fun IrClass.isActivity():Boolean = this.superTypes.any {
    listOf(FqName("android.app.Activity")).contains(it.classFqName)
}

fun IrClass.isView():Boolean = this.superTypes.any {
    listOf(FqName("android.app.View"),FqName("android.app.ViewGroup")).contains(it.classFqName)
}

fun IrClass.isFragment(): Boolean {
    return this.superTypes.any {
        listOf(FqName(AndroidConst.FRAGMENT_FQNAME), FqName(AndroidConst.SUPPORT_FRAGMENT_FQNAME),
            FqName(AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_FQNAME)).contains(it.classFqName)
    }
}
   /* get() = isClassWithFqName(FqNameUnsafe(AndroidConst.FRAGMENT_FQNAME)) ||
            isClassWithFqName(FqNameUnsafe(AndroidConst.SUPPORT_FRAGMENT_FQNAME)) ||
            isClassWithFqName(FqNameUnsafe(AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_FQNAME))*/

val androidView = FqName("androidx.compose.ui.viewinterop.AndroidView")
val modifier = FqName("androidx.compose.ui.Modifier")

val IrProperty.isBindable
    get() = this.toIrBasedDescriptor().annotations.findAnnotation(
    bindViewAnnotation)

private const val root = "androidx.compose.runtime"
private const val internalRoot = "$root.internal"
private val rootFqName = FqName(root)
private val internalRootFqName = FqName(internalRoot)
object ComposeClassIds {
    private fun classIdFor(cname: String) =
        ClassId(rootFqName, Name.identifier(cname))
    internal fun internalClassIdFor(cname: String) =
        ClassId(internalRootFqName, Name.identifier(cname))

    val Composable = classIdFor("Composable")
    val ComposableInferredTarget = classIdFor("ComposableInferredTarget")
    val ComposableLambda = internalClassIdFor("ComposableLambda")
    val ComposableOpenTarget = classIdFor("ComposableOpenTarget")
    val ComposableTarget = classIdFor("ComposableTarget")
    val ComposeVersion = classIdFor("ComposeVersion")
    val Composer = classIdFor("Composer")
    val FunctionKeyMetaClass = internalClassIdFor("FunctionKeyMetaClass")
    val FunctionKeyMeta = internalClassIdFor("FunctionKeyMeta")
    val LiveLiteralFileInfo = internalClassIdFor("LiveLiteralFileInfo")
    val LiveLiteralInfo = internalClassIdFor("LiveLiteralInfo")
    val NoLiveLiterals = classIdFor("NoLiveLiterals")
    val State = classIdFor("State")
    val StabilityInferred = internalClassIdFor("StabilityInferred")
}

var viewClazz = ClassId(FqName("android.view"), Name.identifier("View"))
val androidViewClazz = CallableId(FqName("androidx.compose.ui.viewinterop"), Name.identifier("AndroidView"))
val modifierCall = CallableId(FqName("androidx.compose.ui"), Name.identifier("Modifier"))
fun IrPluginContext.irType(
    classId: ClassId,
    nullable: Boolean = false,
    arguments: List<IrTypeArgument> = emptyList()
): IrType = referenceClass(classId)!!.createType(hasQuestionMark = nullable, arguments = arguments)
