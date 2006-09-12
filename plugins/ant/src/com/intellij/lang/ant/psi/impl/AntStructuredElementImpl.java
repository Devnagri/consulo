package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {

  protected AntTypeDefinition myDefinition;
  private boolean myDefinitionCloned;
  private AntElement myIdElement;
  private AntElement myNameElement;
  @NonNls private String myNameElementAttribute;
  private int myLastFoundElementOffset = -1;
  private AntElement myLastFoundElement;
  private boolean myIsImported;
  protected boolean myInGettingChildren;

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement);
    myNameElementAttribute = nameElementAttribute;
    getIdElement();
    getNameElement();
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    this(parent, sourceElement, AntFileImpl.NAME_ATTR);
  }

  public AntStructuredElementImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  final AntTypeDefinition definition,
                                  @NonNls final String nameElementAttribute) {
    this(parent, sourceElement, nameElementAttribute);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(getSourceElement().getName());
    if (definition != null && !definition.getTypeId().equals(id)) {
      myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
      myDefinition.setTypeId(id);
      myDefinitionCloned = true;
    }
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, AntFileImpl.NAME_ATTR);
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntStructuredElement[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    if (hasNameElement()) {
      return computeAttributeValue(getNameElement().getName());
    }
    if (hasIdElement()) {
      return computeAttributeValue(getIdElement().getName());
    }
    return super.getName();
  }

  public PsiElement setName(@NotNull final String name) throws IncorrectOperationException {
    if (hasNameElement()) {
      getNameElement().setName(name);
    }
    else if (hasIdElement()) {
      getIdElement().setName(name);
    }
    else {
      super.setName(name);
    }
    return this;
  }

  public PsiElement findElementAt(int offset) {
    if (offset == myLastFoundElementOffset) {
      return myLastFoundElement;
    }
    final PsiElement foundElement = super.findElementAt(offset);
    if (foundElement != null) {
      myLastFoundElement = (AntElement)foundElement;
      myLastFoundElementOffset = offset;
    }
    return foundElement;
  }

  public AntTypeDefinition getTypeDefinition() {
    AntTypeDefinition def = myDefinition;
    if (def != null) {
      final PsiNamedElement definingElement = (PsiNamedElement)def.getDefiningElement();
      if (definingElement != null && !getSourceElement().getName().equals(definingElement.getName())) {
        myDefinition = def = null;
        super.clearCaches();
      }
    }
    return def;
  }

  public void registerCustomType(final AntTypeDefinition def) {
    if (myDefinition != null) {
      if (!myDefinitionCloned) {
        myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
        myDefinitionCloned = true;
      }
      myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
    }
    getAntFile().registerCustomType(def);
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    if (myDefinition != null && myDefinitionCloned) {
      myDefinition.unregisterNestedType(def.getTypeId());
    }
    getAntFile().unregisterCustomType(def);
  }

  public boolean hasImportedTypeDefinition() {
    return myIsImported;
  }

  void setImportedTypeDefinition(boolean imported) {
    myIsImported = imported;
  }

  @Nullable
  public PsiFile findFileByName(final String name, final boolean ignoreBasedir) {
    if (name == null) return null;
    final AntFileImpl antFile = PsiTreeUtil.getParentOfType(this, AntFileImpl.class);
    if (antFile == null) return null;
    VirtualFile vFile = antFile.getContainingPath();
    if (vFile == null) return null;
    String projectPath = vFile.getPath();
    if (!ignoreBasedir) {
      final String baseDir = antFile.getAntProject().getBaseDir();
      if (baseDir != null && baseDir.length() > 0) {
        projectPath = new File(projectPath, baseDir).getAbsolutePath();
      }
    }
    final String fileName = computeAttributeValue(name);
    File file = new File(fileName);
    if (!file.isAbsolute()) {
      file = new File(projectPath, fileName);
    }
    vFile = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath().replace(File.separatorChar, '/'));
    if (vFile == null) return null;
    return antFile.getViewProvider().getManager().findFile(vFile);
  }

  @Nullable
  public String computeAttributeValue(final String value) {
    if (value == null) return null;
    final Set<PsiElement> set = PsiElementSetSpinAllocator.alloc();
    try {
      return computeAttributeValue(value, set);
    }
    finally {
      PsiElementSetSpinAllocator.dispose(set);
    }
  }

  public boolean hasNameElement() {
    return getNameElement() != ourNull;
  }

  public boolean hasIdElement() {
    return getIdElement() != ourNull;
  }

  public String getFileReferenceAttribute() {
    return null;
  }

  public boolean isTypeDefined() {
    return myDefinition != null && myDefinition.getDefiningElement() instanceof AntTypeDefImpl;
  }

  public boolean isPresetDefined() {
    return myDefinition != null && myDefinition.getClassName().startsWith(AntPresetDefImpl.ANT_PRESETDEF_NAME);
  }

  public void clearCaches() {
    super.clearCaches();
    myIdElement = null;
    myNameElement = null;
    myLastFoundElementOffset = -1;
    myLastFoundElement = null;
  }


  public AntElement lightFindElementAt(int offset) {
    if (offset == myLastFoundElementOffset) {
      return myLastFoundElement;
    }
    return super.lightFindElementAt(offset);
  }

  public int getTextOffset() {
    if (hasNameElement()) {
      return getNameElement().getTextOffset();
    }
    if (hasIdElement()) {
      return getIdElement().getTextOffset();
    }
    return super.getTextOffset();
  }

  protected AntElement[] getChildrenInner() {
    if (!myInGettingChildren) {
      myInGettingChildren = true;
      try {
        final List<AntElement> children = new ArrayList<AntElement>();
        if (hasIdElement()) {
          children.add(getIdElement());
        }
        if (hasNameElement()) {
          children.add(getNameElement());
        }
        for (final PsiElement element : getSourceElement().getChildren()) {
          if (element instanceof XmlElement) {
            final AntElement antElement = AntElementFactory.createAntElement(this, (XmlElement)element);
            if (antElement != null) {
              children.add(antElement);
              if (antElement instanceof AntStructuredElement) {
                antElement.getChildren();
              }
            }
          }
        }
        final int count = children.size();
        return (count > 0) ? children.toArray(new AntElement[count]) : AntElement.EMPTY_ARRAY;
      }
      finally {
        myInGettingChildren = false;
      }
    }
    return AntElement.EMPTY_ARRAY;
  }

  @NotNull
  protected AntElement getIdElement() {
    if (myIdElement == null) {
      myIdElement = ourNull;
      final XmlTag se = getSourceElement();
      if (se.isValid()) {
        final XmlAttribute idAttr = se.getAttribute(AntFileImpl.ID_ATTR, null);
        if (idAttr != null) {
          final XmlAttributeValue valueElement = idAttr.getValueElement();
          if (valueElement != null) {
            myIdElement = new AntNameElementImpl(this, valueElement);
            getAntProject().registerRefId(myIdElement.getName(), this);
          }
        }
      }
    }
    return myIdElement;
  }

  @NotNull
  protected AntElement getNameElement() {
    if (myNameElement == null) {
      myNameElement = ourNull;
      final XmlTag se = getSourceElement();
      if (se.isValid()) {
        final XmlAttribute nameAttr = se.getAttribute(myNameElementAttribute, null);
        if (nameAttr != null) {
          final XmlAttributeValue valueElement = nameAttr.getValueElement();
          if (valueElement != null) {
            myNameElement = new AntNameElementImpl(this, valueElement);
          }
        }
      }
    }
    return myNameElement;
  }

  @NonNls
  protected String getNameElementAttribute() {
    return myNameElementAttribute;
  }

  /**
   * Cycle-safe computation of an attribute value with resolving properties.
   *
   * @param value
   * @param elementStack
   * @return
   */
  protected String computeAttributeValue(String value, Set<PsiElement> elementStack) {
    elementStack.add(this);
    int startProp = 0;
    while ((startProp = value.indexOf("${", startProp)) >= 0) {
      final int endProp = value.indexOf('}', startProp + 2);
      if (endProp <= startProp + 2) {
        startProp += 2;
        continue;
      }
      final String prop = value.substring(startProp + 2, endProp);
      final PsiElement propElement = resolveProperty(this, prop);
      if (elementStack.contains(propElement)) {
        return value;
      }
      String resolvedValue = null;
      if (propElement instanceof AntProperty) {
        final AntProperty antProperty = (AntProperty)propElement;
        resolvedValue = antProperty.getValue(prop);
        if (resolvedValue != null) {
          resolvedValue = ((AntStructuredElementImpl)antProperty).computeAttributeValue(resolvedValue, elementStack);
        }
      }
      else if (propElement instanceof Property) {
        resolvedValue = ((Property)propElement).getValue();
      }
      if (resolvedValue == null) {
        startProp += 2;
      }
      else {
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          builder.append(value, 0, startProp);
          builder.append(resolvedValue);
          if (endProp < value.length() - 1) {
            builder.append(value, endProp + 1, value.length());
          }
          value = builder.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    }
    return value;
  }
}