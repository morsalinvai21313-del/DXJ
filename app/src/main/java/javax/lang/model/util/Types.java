package javax.lang.model.util;

import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

public interface Types {
    Element asElement(TypeMirror t);
    boolean isSameType(TypeMirror t1, TypeMirror t2);
    boolean isSubtype(TypeMirror t1, TypeMirror t2);
    boolean isAssignable(TypeMirror t1, TypeMirror t2);
    boolean contains(TypeMirror t1, TypeMirror t2);
    boolean isSubsignature(ExecutableType m1, ExecutableType m2);
    List<? extends TypeMirror> directSupertypes(TypeMirror t);
    TypeMirror erasure(TypeMirror t);
    TypeElement boxedClass(PrimitiveType p);
    PrimitiveType unboxedType(TypeMirror t);
    TypeMirror capture(TypeMirror t);
    PrimitiveType getPrimitiveType(TypeKind kind);
    NullType getNullType();
    NoType getNoType(TypeKind kind);
    ArrayType getArrayType(TypeMirror componentType);
    WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound);
    DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs);
    DeclaredType getDeclaredType(DeclaredType containing, TypeElement typeElem, TypeMirror... typeArgs);
    TypeMirror asMemberOf(DeclaredType containing, Element element);
}
