/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.like.script;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.like.LikeConfiguration;
import org.xwiki.like.LikeException;
import org.xwiki.like.LikeManager;
import org.xwiki.like.LikedEntity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.stability.Unstable;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;

/**
 * Script service for manipulating Like informations.
 *
 * @version $Id$
 * @since 12.6RC1
 */
@Component
@Singleton
@Named("like")
@Unstable
public class LikeScriptService implements ScriptService
{
    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private LikeManager likeManager;

    @Inject
    private LikeConfiguration likeConfiguration;

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    @Inject
    private Logger logger;

    private Right getLikeRight()
    {
        return this.likeManager.getLikeRight();
    }

    /**
     * Check if current user is allowed to use Like on the given reference.
     * @param entityReference the reference on which to use like.
     * @return {@code true} only if current user has Like right on the reference.
     */
    public boolean isAuthorized(EntityReference entityReference)
    {
        return this.authorizationManager.hasAccess(getLikeRight(),
            this.contextProvider.get().getUserReference(), entityReference);
    }

    /**
     * Check if the display button should be displayed: should be {@code true} if current user is authorized to use like
     * (see {@link #isAuthorized(EntityReference)}) or if the configuration is set to always display it
     * (see {@link LikeConfiguration#alwaysDisplayButton()}).
     *
     * @param entityReference the reference for which to display the button
     * @return {@code true} only if the button should be displayed.
     */
    public boolean displayButton(EntityReference entityReference)
    {
        return this.likeConfiguration.alwaysDisplayButton() || this.isAuthorized(entityReference);
    }

    /**
     * Perform a like on the given reference with the current user, only if allowed.
     *
     * @param entityReference the reference on which to perform a like.
     * @return an entity containing like information if the operation succeeded, else return an empty optional.
     */
    public Optional<LikedEntity> like(EntityReference entityReference)
    {
        XWikiContext xWikiContext = this.contextProvider.get();
        DocumentReference currentUser = xWikiContext.getUserReference();

        if (entityReference instanceof DocumentReference) {
            DocumentReference documentReference = (DocumentReference) entityReference;
            if (isAuthorized(documentReference)) {
                UserReference userReference = this.userReferenceResolver.resolve(currentUser);
                try {
                    return Optional.of(this.likeManager.saveLike(userReference, documentReference));
                } catch (LikeException e) {
                    this.logger.warn("Error while liking [{}] by [{}]", documentReference, currentUser,
                        ExceptionUtils.getRootCause(e));
                }
            } else {
                this.logger.warn("[{}] is not authorized to like [{}].", currentUser, documentReference);
            }
        } else {
            this.logger.warn("Like is only implemented for document for now. (Called with [{}])", entityReference);
        }
        return Optional.empty();
    }

    /**
     * Perform a unlike on the given reference with the current user, only if allowed.
     *
     * @param entityReference the reference on which to perform a like.
     * @return an entity containing like information if the operation succeeded, else return an empty optional.
     */
    public Optional<LikedEntity> unlike(EntityReference entityReference)
    {
        XWikiContext xWikiContext = this.contextProvider.get();
        DocumentReference currentUser = xWikiContext.getUserReference();

        if (entityReference instanceof DocumentReference) {
            DocumentReference documentReference = (DocumentReference) entityReference;
            if (this.isAuthorized(documentReference)) {
                UserReference userReference = this.userReferenceResolver.resolve(currentUser);
                try {
                    this.likeManager.removeLike(userReference, entityReference);
                    return Optional.of(this.likeManager.getEntityLikes(entityReference));
                } catch (LikeException e) {
                    this.logger.warn("Error while unliking [{}] by [{}]", documentReference, currentUser,
                        ExceptionUtils.getRootCause(e));
                }
            } else {
                this.logger.warn("[{}] is not authorized to unlike [{}].", currentUser, documentReference);
            }
        } else {
            this.logger.warn("Unlike is only implemented for document for now. (Called with [{}])", entityReference);
        }
        return Optional.empty();
    }

    /**
     * Retrieve like information for the given reference.
     *
     * @param entityReference the reference for which to retrieve like information.
     * @return an entity containing like informations, or an empty optional in case of problem.
     */
    public Optional<LikedEntity> getLikes(EntityReference entityReference)
    {
        try {
            return Optional.of(this.likeManager.getEntityLikes(entityReference));
        } catch (LikeException e) {
            this.logger.warn("Error while getting like information for [{}]", entityReference,
                ExceptionUtils.getRootCause(e));
        }
        return Optional.empty();
    }

    /**
     * Determine if the current user already liked the given reference.
     * @param entityReference the reference for which to check if the current liked it or not already.
     * @return {@code true} if the entity has been already liked.
     */
    public boolean isLiked(EntityReference entityReference)
    {
        UserReference userReference = this.userReferenceResolver.resolve(this.contextProvider.get().getUserReference());

        try {
            return this.likeManager.isLiked(userReference, entityReference);
        } catch (LikeException e) {
            this.logger.warn("Error while checking if [{}] is liked by [{}]", entityReference, userReference,
                ExceptionUtils.getRootCause(e));
        }
        return false;
    }
}
