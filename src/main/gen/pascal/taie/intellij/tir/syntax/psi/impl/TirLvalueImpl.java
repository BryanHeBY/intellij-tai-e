// This is a generated file. Not intended for manual editing.
package pascal.taie.intellij.tir.syntax.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pascal.taie.intellij.tir.syntax.psi.TirFieldSig;
import pascal.taie.intellij.tir.syntax.psi.TirIdentifierVariable;
import pascal.taie.intellij.tir.syntax.psi.TirLvalue;
import pascal.taie.intellij.tir.syntax.psi.TirVisitor;

import java.util.List;

public class TirLvalueImpl extends ASTWrapperPsiElement implements TirLvalue {

  public TirLvalueImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull TirVisitor visitor) {
    visitor.visitLvalue(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof TirVisitor) accept((TirVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public TirFieldSig getFieldSig() {
      return findChildByClass(TirFieldSig.class);
  }

    @Override
    @NotNull
    public List<TirIdentifierVariable> getIdentifierVariableList() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, TirIdentifierVariable.class);
    }

}
