/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.repository.helm.internal.restore;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Named;

import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.transaction.TransactionalTouchBlob;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.transaction.UnitOfWork;
import org.sonatype.repository.helm.HelmAttributes;
import org.sonatype.repository.helm.HelmFacet;
import org.sonatype.repository.helm.HelmRestoreFacet;

import static org.sonatype.nexus.repository.storage.ComponentEntityAdapter.P_VERSION;
import static org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter.P_NAME;

/**
 * @since 1.1.next
 */
@Named
public class HelmRestoreFacetImpl
    extends FacetSupport
    implements HelmRestoreFacet
{
  private HelmFacet helmFacet;

  @Override
  protected void doInit(final Configuration configuration) throws Exception {
    super.doInit(configuration);
    helmFacet = facet(HelmFacet.class);
  }

  @Override
  @TransactionalTouchBlob
  public void restore(final AssetBlob assetBlob, final String path) throws IOException {
    StorageTx tx = UnitOfWork.currentTx();
    Bucket bucket = tx.findBucket(getRepository());
    HelmAttributes attributes =
        helmFacet.getHelmAttributeParser().getAttributesFromInputStream(assetBlob.getBlob().getInputStream());

    Asset asset;
    if (componentRequired(path)) {
      asset = helmFacet.findOrCreateAssetWithComponent(path, tx, bucket, attributes);
    }
    else {
      asset = helmFacet.findOrCreateAssetWithAttributes(path, tx, bucket, attributes);
    }

    tx.attachBlob(asset, assetBlob);
    Content.applyToAsset(asset, Content.maintainLastModified(asset, new AttributesMap()));
    tx.saveAsset(asset);
  }

  @Override
  @TransactionalTouchBlob
  public boolean assetExists(final String path) {
    final StorageTx tx = UnitOfWork.currentTx();
    return tx.findAssetWithProperty(P_NAME, path, tx.findBucket(getRepository())) != null;
  }

  @Override
  public boolean componentRequired(final String name) {
    return name.endsWith(".tgz");
  }

  @Override
  public Query getComponentQuery(final HelmAttributes attributes) {
    return Query.builder().where(P_NAME).eq(attributes.getName())
        .and(P_VERSION).eq(attributes.getVersion()).build();
  }

  @Override
  public HelmAttributes extractComponentAttributesFromArchive(final InputStream is) throws IOException {
    return helmFacet.getHelmAttributeParser().getAttributesFromInputStream(is);
  }
}
