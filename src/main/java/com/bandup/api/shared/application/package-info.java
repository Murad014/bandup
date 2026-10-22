/**
 * Shared kernel — application layer: use cases (one transaction changes one aggregate) and
 * ports for services they need. May use only Spring {@code @Service} and
 * {@code @Transactional}; depends only on the domain.
 */
package com.bandup.api.shared.application;
