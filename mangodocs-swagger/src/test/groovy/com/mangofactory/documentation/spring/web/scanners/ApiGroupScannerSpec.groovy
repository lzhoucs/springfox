package com.mangofactory.documentation.spring.web.scanners
import com.mangofactory.documentation.service.PathProvider
import com.mangofactory.documentation.service.model.ApiInfo
import com.mangofactory.documentation.service.model.ApiKey
import com.mangofactory.documentation.service.model.ApiListingReference
import com.mangofactory.documentation.service.model.Group
import com.mangofactory.documentation.service.model.ResourceListing
import com.mangofactory.documentation.spi.service.contexts.RequestMappingContext
import com.mangofactory.documentation.spring.web.DocumentationContextSpec
import com.mangofactory.documentation.spring.web.mixins.RequestMappingSupport
import com.mangofactory.documentation.spring.web.ordering.ResourceListingLexicographicalOrdering
import com.mangofactory.documentation.spring.web.ordering.ResourceListingPositionalOrdering
import com.mangofactory.documentation.swagger.web.AbsolutePathProvider

import static com.google.common.collect.Maps.*

@Mixin([RequestMappingSupport])
class ApiGroupScannerSpec extends DocumentationContextSpec {

  ApiListingReferenceScanner listingReferenceScanner = Mock(ApiListingReferenceScanner)
  ApiListingScanner listingScanner = Mock(ApiListingScanner)
  ApiGroupScanner  swaggerApiResourceListing = new ApiGroupScanner(listingReferenceScanner, listingScanner)

  def "default swagger resource"() {
    when: "I create a swagger resource"
      listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult([], newHashMap())
    and:
      Group scanned = swaggerApiResourceListing.scan(context())

    then: "I should should have the correct defaults"
      ResourceListing resourceListing = scanned.resourceListing
      def apiListingReferenceList = resourceListing.getApis()
      def authorizationTypes = resourceListing.getAuthorizations()

      scanned.groupName == "default"
      resourceListing.getApiVersion() == "1.0"

      resourceListing.getInfo() != null
      apiListingReferenceList == []
      authorizationTypes == []
  }

  def "resource with api info"() {
    given:
      ApiInfo expected = new ApiInfo("title", "description", "1.0", "terms", "contact", "license", "licenseUrl")
    when:
      plugin
              .groupName("groupName")
              .includePatterns(".*")
              .apiInfo(expected)
              .configure(contextBuilder)
      listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult([], newHashMap())
    and:
      Group scanned = swaggerApiResourceListing.scan(context())
    then:
      ApiInfo actual = scanned.getResourceListing().getInfo()
      actual.getTitle() == expected.getTitle()
      actual.getVersion() == expected.getVersion()
      actual.getDescription() == expected.getDescription()
      actual.getTermsOfServiceUrl() == expected.getTermsOfServiceUrl()
      actual.getContact() == expected.getContact()
      actual.getLicense() == expected.getLicense()
      actual.getLicenseUrl() == expected.getLicenseUrl()
  }

  def "resource with authorization types"() {
    given:
      ApiKey apiKey = new ApiKey("api_key", "header")
    when:
      plugin
              .groupName("groupName")
              .includePatterns(".*")
              .authorizationTypes([apiKey])
              .configure(contextBuilder)
      listingReferenceScanner.scan(_) >> new ApiListingReferenceScanResult([], newHashMap())
    and:
      Group scanned = swaggerApiResourceListing.scan(context())
    then:
      ResourceListing resourceListing = scanned.resourceListing
      def authorizationTypes = resourceListing.getAuthorizations()
      def apiKeyAuthType = authorizationTypes[0]
      apiKeyAuthType instanceof ApiKey
      apiKeyAuthType.keyname == "api_key"
      apiKeyAuthType.passAs == "header"
  }

  def "resource with mocked apis"() {
    given:
      PathProvider pathProvider = new AbsolutePathProvider(servletContext: servletContext())
      plugin
              .groupName("groupName")
              .includePatterns(".*")
              .pathProvider(pathProvider)
              .configure(contextBuilder)

      RequestMappingContext requestMappingContext = new RequestMappingContext(context(), (requestMappingInfo
              ("somePath/")), dummyHandlerMethod())

    when:
      listingReferenceScanner.scan(_) >>
              new ApiListingReferenceScanResult([Mock(ApiListingReference)], [resourceGroup: [requestMappingContext]])
    and:
      Group scanned = swaggerApiResourceListing.scan(context())
      scanned.resourceListing != null

    then:
      scanned.groupName == "groupName"
      1 * listingReferenceScanner.scan(_) >>
              new ApiListingReferenceScanResult([Mock(ApiListingReference)], [resourceGroup: [requestMappingContext]])
  }

  def "Should sort based on position"() {
    given:
      plugin
              .groupName("groupName")
              .includePatterns(".*")
              .apiListingReferenceOrdering(ordering)
              .configure(contextBuilder)

      def refs = [
            new ApiListingReference("/b", 'b', 1),
            new ApiListingReference("/a", 'a', 2)
      ]
      listingReferenceScanner.scan(_) >>
              new ApiListingReferenceScanResult(refs, newHashMap())

    when:
      Group scanned = swaggerApiResourceListing.scan(context())
      def apis = scanned.resourceListing.getApis()
    then:
      apis[0].position == firstPosition
      apis[0].path == firstPath

    where:
      ordering                                     | firstPath | firstPosition
      new ResourceListingPositionalOrdering()      | '/b'      | 1
      new ResourceListingLexicographicalOrdering() | '/a'      | 2
  }
}