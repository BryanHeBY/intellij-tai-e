// This is a generated file. Not intended for manual editing.
package pascal.taie.intellij.tir.syntax.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface TirRvalue extends PsiElement {

  @Nullable
  TirBinaryExpr getBinaryExpr();

  @Nullable
  TirFieldSig getFieldSig();

  @Nullable
  TirIdentifierType getIdentifierType();

  @Nullable
  TirIdentifierVariable getIdentifierVariable();

  @Nullable
  TirInvokeExpr getInvokeExpr();

  @Nullable
  TirUnaryExpr getUnaryExpr();

}
