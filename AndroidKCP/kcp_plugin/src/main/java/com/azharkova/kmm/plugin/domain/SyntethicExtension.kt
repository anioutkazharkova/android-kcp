package com.azharkova.kcp.plugin.domain

import com.azharkova.kcp.plugin.util.isToComposable
import com.azharkova.kcp.plugin.util.isUseCase
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.*

class SyntethicExtension(): SyntheticResolveExtension {

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? =
        if (thisDescriptor.isUsecase || thisDescriptor.isToComposable()) {
            Names.DEFAULT_COMPANION
        } else {
            null
        }

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> =
        if (thisDescriptor.isCompanionObject) {
            if (thisDescriptor.isUsecaseCompanion) {
                listOf(Names.USECASE_METHOD)
            } else if (thisDescriptor.isToComposableCompanion){
               listOf(Names.VIEW_METHOD)
            }else {
                emptyList()
            }
        }else {
            emptyList()
        }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        if (name == Names.USECASE_METHOD) {
            val classDescriptor = getForCompanion(thisDescriptor) ?: return

            var params = mutableListOf<KotlinType>()

            val any = thisDescriptor.module.findClassAcrossModuleDependencies(
                ClassIds.ANY
            )?.defaultType
            params.addAll(listOf(any!!, any!!))
            result.add(createUsecaseGetterDescriptor(thisDescriptor, classDescriptor, params))
        }
        if (name == Names.VIEW_METHOD) {
            val classDescriptor = getForCompanion(thisDescriptor) ?: return

            var params = mutableListOf<KotlinType>()

            val any = thisDescriptor.module.findClassAcrossModuleDependencies(
                ClassIds.ANY
            )?.defaultType
            params.addAll(listOf(any!!, any!!))
            result.add(createComposableDescriptor(thisDescriptor, classDescriptor, params))
        }
    }

    private fun getForCompanion(descriptor: ClassDescriptor): ClassDescriptor? =
        if (descriptor.isUsecaseCompanion || descriptor.isToComposableCompanion) {
            descriptor.containingDeclaration as ClassDescriptor
        } else {
            null
        }
}

val ClassDescriptor.isToComposableCompanion
    get() = isCompanionObject && (containingDeclaration as ClassDescriptor).isToComposable()

val ClassDescriptor.isUsecaseCompanion
    get() = isCompanionObject && (containingDeclaration as ClassDescriptor).isUseCase()

val ClassDescriptor.isUsecase
    get() = this.isUseCase()


fun createUsecaseGetterDescriptor(
    companionClass: ClassDescriptor,
    clazz: ClassDescriptor,
    params: List<KotlinType>
): SimpleFunctionDescriptor {
    val function = SimpleFunctionDescriptorImpl.create(
        companionClass,
        Annotations.EMPTY,
        Names.USECASE_METHOD,
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        companionClass.source
    )

    val usecaseClass = clazz.module.findClassAcrossModuleDependencies(
        ClassIds.COROUTINE_USE_CASE
    )!!


    val returnType = KotlinTypeFactory.simpleNotNullType(
        TypeAttributes.Empty,
       usecaseClass,
        emptyList()
      /*  params.map {
            TypeProjectionImpl(it)
        }*/
    )


    function.initialize(
        null,
        companionClass.thisAsReceiverParameter,
        emptyList(),
        emptyList(),
        returnType,
        Modality.FINAL,
        DescriptorVisibilities.PUBLIC
    )

    return function
}

fun createComposableDescriptor(
    companionClass: ClassDescriptor,
    clazz: ClassDescriptor,
    params: List<KotlinType>
): SimpleFunctionDescriptor {
    val function = SimpleFunctionDescriptorImpl.create(
        companionClass,
        Annotations.EMPTY,
        Names.VIEW_METHOD,
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        companionClass.source
    )

    val unitClass = clazz.module.findClassAcrossModuleDependencies(
        ClassIds.UNIT
    )!!



    val returnType = KotlinTypeFactory.simpleNotNullType(
        TypeAttributes.Empty,
        unitClass,
        emptyList()
        /*  params.map {
              TypeProjectionImpl(it)
          }*/
    )


    function.initialize(
        null,
        companionClass.thisAsReceiverParameter,
        emptyList(),
        emptyList(),
        returnType,
        Modality.FINAL,
        DescriptorVisibilities.PUBLIC
    )

    return function
}