package com.azharkova.kcp.plugin.domain

import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils


object AndroidConst {
    val SYNTHETIC_PACKAGE: String = "kotlinx.android.synthetic"
    val SYNTHETIC_PACKAGE_PATH_LENGTH = SYNTHETIC_PACKAGE.count { it == '.' } + 1

    val SYNTHETIC_SUBPACKAGES: List<String> = SYNTHETIC_PACKAGE.split('.').fold(arrayListOf<String>()) { list, segment ->
        val prevSegment = list.lastOrNull()?.let { "$it." } ?: ""
        list += "$prevSegment$segment"
        list
    }

    val ANDROID_NAMESPACE: String = "http://schemas.android.com/apk/res/android"
    val ID_ATTRIBUTE_NO_NAMESPACE: String = "id"
    val CLASS_ATTRIBUTE_NO_NAMESPACE: String = "class"

    private val IDENTIFIER_WORD_REGEX = "[(?:\\p{L}\\p{M}*)0-9_\\.\\:\\-]+"
    val IDENTIFIER_REGEX = "^@(\\+)?(($IDENTIFIER_WORD_REGEX)\\:)?id\\/($IDENTIFIER_WORD_REGEX)$".toRegex()

    val CLEAR_FUNCTION_NAME = "clearFindViewByIdCache"


    //TODO FqName / ClassId

    val VIEW_FQNAME = "android.view.View"
    val VIEWSTUB_FQNAME = "android.view.ViewStub"

    val ACTIVITY_FQNAME = "android.app.Activity"
    val FRAGMENT_FQNAME = "android.app.Fragment"
    val DIALOG_FQNAME = "android.app.Dialog"
    val SUPPORT_V4_PACKAGE = "android.support.v4"
    val SUPPORT_FRAGMENT_FQNAME = "$SUPPORT_V4_PACKAGE.app.Fragment"
    val SUPPORT_FRAGMENT_ACTIVITY_FQNAME = "$SUPPORT_V4_PACKAGE.app.FragmentActivity"
    val ANDROIDX_SUPPORT_FRAGMENT_FQNAME = "androidx.fragment.app.Fragment"
    val ANDROIDX_SUPPORT_FRAGMENT_ACTIVITY_FQNAME = "androidx.fragment.app.FragmentActivity"

    val IGNORED_XML_WIDGET_TYPES = setOf("requestFocus", "merge", "tag", "check", "blink")

    val FQNAME_RESOLVE_PACKAGES = listOf("android.widget", "android.webkit", "android.view")
}

fun getJavaIdentifierNameForResourceName(styleName: String) = buildString {
    for (char in styleName) {
        when (char) {
            '.', '-', ':' -> append('_')
            else -> append(char)
        }
    }
}

fun isWidgetTypeIgnored(xmlType: String): Boolean {
    return (xmlType.isEmpty() || xmlType in AndroidConst.IGNORED_XML_WIDGET_TYPES)
}

internal fun <T> List<T>.forEachUntilLast(operation: (T) -> Unit) {
    val lastIndex = lastIndex
    forEachIndexed { i, t ->
        if (i < lastIndex) {
            operation(t)
        }
    }
}
/**
 * A base interface for all view holders supporting Android Extensions-style view access.
 */
public interface LayoutContainer {
    /** Returns the root holder view. */
   //public val containerView: View?
}


enum class AndroidContainerType(val className: String) {
    ACTIVITY(AndroidConst.ACTIVITY_FQNAME),
    FRAGMENT(AndroidConst.FRAGMENT_FQNAME),
    DIALOG(AndroidConst.DIALOG_FQNAME),
    ANDROIDX_SUPPORT_FRAGMENT_ACTIVITY(
        AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_ACTIVITY_FQNAME
    ),
    ANDROIDX_SUPPORT_FRAGMENT(
        AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_FQNAME
    ),
    SUPPORT_FRAGMENT_ACTIVITY(
        AndroidConst.SUPPORT_FRAGMENT_ACTIVITY_FQNAME
    ),
    SUPPORT_FRAGMENT(
        AndroidConst.SUPPORT_FRAGMENT_FQNAME
    ),
    VIEW(AndroidConst.VIEW_FQNAME),

    //LAYOUT_CONTAINER(LayoutContainer::class.java.canonicalName, doesSupportCache = true),
    UNKNOWN("");

    val internalClassName: String = className.replace('.', '/')
    //val fqName: FqName = FqName(this.className)


    companion object {

        private val LAYOUT_CONTAINER_FQNAME = LayoutContainer::class.java.canonicalName
        fun get(descriptor: ClassifierDescriptor): AndroidContainerType {
            fun getContainerTypeInternal(name: String): AndroidContainerType? = when (name) {
                AndroidConst.ACTIVITY_FQNAME -> AndroidContainerType.ACTIVITY
                AndroidConst.FRAGMENT_FQNAME -> AndroidContainerType.FRAGMENT
                AndroidConst.DIALOG_FQNAME -> AndroidContainerType.DIALOG
                AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_ACTIVITY_FQNAME -> AndroidContainerType.ANDROIDX_SUPPORT_FRAGMENT_ACTIVITY
                AndroidConst.ANDROIDX_SUPPORT_FRAGMENT_FQNAME -> AndroidContainerType.ANDROIDX_SUPPORT_FRAGMENT
                AndroidConst.SUPPORT_FRAGMENT_ACTIVITY_FQNAME -> AndroidContainerType.SUPPORT_FRAGMENT_ACTIVITY
                AndroidConst.SUPPORT_FRAGMENT_FQNAME -> AndroidContainerType.SUPPORT_FRAGMENT
                AndroidConst.VIEW_FQNAME -> AndroidContainerType.VIEW
                // LAYOUT_CONTAINER_FQNAME -> AndroidContainerType.LAYOUT_CONTAINER
                else -> null
            }

            getContainerTypeInternal(
                DescriptorUtils.getFqName(descriptor).asString()
            )?.let { return it }

            for (supertype in descriptor.typeConstructor.supertypes) {
                val declarationDescriptor = supertype.constructor.declarationDescriptor
                if (declarationDescriptor != null) {
                    val containerType = get(declarationDescriptor)
                    if (containerType != AndroidContainerType.UNKNOWN) return containerType
                }
            }

            return AndroidContainerType.UNKNOWN
        }
    }
}
