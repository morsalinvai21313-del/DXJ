package javax.lang.model.type;

import java.lang.annotation.Annotation;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;

public interface TypeMirror {
    TypeKind getKind();
    boolean equals(Object obj);
    int hashCode();
    String toString();
    <R, P> R accept(TypeVisitor<R, P> v, P p);
    List<? extends AnnotationMirror> getAnnotationMirrors();
    <A extends Annotation> A getAnnotation(Class<A> annotationType);
    <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType);
}
