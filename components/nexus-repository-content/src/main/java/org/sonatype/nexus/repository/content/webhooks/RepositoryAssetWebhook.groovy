/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.content.webhooks

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

import org.sonatype.nexus.audit.InitiatorProvider
import org.sonatype.nexus.common.app.FeatureFlag
import org.sonatype.nexus.common.entity.EntityId
import org.sonatype.nexus.common.node.NodeAccess
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.content.Asset
import org.sonatype.nexus.repository.content.event.asset.AssetCreatedEvent
import org.sonatype.nexus.repository.content.event.asset.AssetDeletedEvent
import org.sonatype.nexus.repository.content.event.asset.AssetEvent
import org.sonatype.nexus.repository.content.event.asset.AssetUpdatedEvent
import org.sonatype.nexus.repository.content.store.InternalIds
import org.sonatype.nexus.repository.rest.api.RepositoryItemIDXO
import org.sonatype.nexus.repository.webhooks.RepositoryWebhook
import org.sonatype.nexus.webhooks.Webhook
import org.sonatype.nexus.webhooks.WebhookPayload
import org.sonatype.nexus.webhooks.WebhookRequest

import com.google.common.eventbus.AllowConcurrentEvents
import com.google.common.eventbus.Subscribe
import org.apache.commons.lang.StringUtils

/**
 * Repository {@link Asset} {@link Webhook}.
 *
 * @since 3.next
 */
@FeatureFlag(name = "nexus.datastore.enabled")
@Named
@Singleton
class RepositoryAssetWebhook
    extends RepositoryWebhook
{
  public static final String NAME = 'asset'

  @Inject
  NodeAccess nodeAccess

  @Inject
  InitiatorProvider initiatorProvider

  @Override
  String getName() {
    return NAME
  }

  private static enum EventAction
  {
    CREATED,
    UPDATED,
    DELETED
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final AssetCreatedEvent event) {
    maybeQueue(event, EventAction.CREATED)
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final AssetUpdatedEvent event) {
    maybeQueue(event, EventAction.UPDATED)
  }

  @Subscribe
  @AllowConcurrentEvents
  void on(final AssetDeletedEvent event) {
    maybeQueue(event, EventAction.DELETED)
  }

  /**
   * Maybe queue {@link WebhookRequest} for event matching subscriptions.
   */
  private void maybeQueue(final AssetEvent event, final EventAction eventAction) {
    Repository repository = event.repository
    Asset asset = event.asset
    EntityId assetId = InternalIds.toExternalId(InternalIds.internalAssetId(asset))

    def payload = new RepositoryAssetWebhookPayload(
        nodeId: nodeAccess.getId(),
        timestamp: new Date(),
        initiator: initiatorProvider.get(),
        repositoryName: repository.name,
        action: eventAction
    )

    payload.asset = new RepositoryAssetWebhookPayload.RepositoryAsset(
        id: assetId.value,
        assetId: new RepositoryItemIDXO(repository.name, assetId.value).value,
        format: repository.format,
        name: StringUtils.stripStart(asset.path(), "/")
    )

    subscriptions.each {
      def configuration = it.configuration as RepositoryWebhook.Configuration
      if (configuration.repository == repository.name) {
        queue(it, payload)
      }
    }
  }

  static class RepositoryAssetWebhookPayload
      extends WebhookPayload
  {
    String repositoryName

    EventAction action

    RepositoryAsset asset

    static class RepositoryAsset
    {
      String id

      String assetId

      String format

      String name
    }
  }
}
