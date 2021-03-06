package com.terrafolio.gradle.plugins.jenkins.service

import com.google.common.annotations.VisibleForTesting
import groovy.xml.StreamingMarkupBuilder
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.XML

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JenkinsRESTServiceImpl implements JenkinsService {
	
	Logger logger = LoggerFactory.getLogger(JenkinsRESTServiceImpl.class);

    def RESTClient client
    def url
    def username
    def password

    public JenkinsRESTServiceImpl(String url, String username, String password) {
        this.url = url
        this.username = username
        this.password = password
    }

    public JenkinsRESTServiceImpl(String url) {
        this.url = url
    }

    @VisibleForTesting
    public JenkinsRESTServiceImpl() {
    }

    def getRestClient() {
        if (client == null) {
            client = new RESTClient(url)
            if (username != null) {
                client.client.addRequestInterceptor(new PreemptiveAuthInterceptor(username, password))
            }
            if (System.getProperty("com.terrafolio.jenkins.ignoressl") != null) {
                client.ignoreSSLIssues()
            }
        }

        return client
    }

    def restServiceGET(path, query) {
    	logger.info("Initializing rest client for:" + url)
        def client = getRestClient()
		logger.info("Getting data from:" + path)
        def response = client.get(path: path, query: query)
        if (response.success) {
            return response.getData()
        } else {
            throw new Exception('REST Service call failed with response code: ' + response.status)
        }
    }

    def restServicePOST(path, query, payload) {
       def client = getRestClient()
       Crumb crumbJson = getCrumb(client)
	   logger.debug("crumbRequestField :" + crumbJson.crumbRequestField)
	   logger.debug("crumb" + crumbJson.crumb)
       def response = client.post(path: path, headers: crumbJson.toMap(), query: query, requestContentType: XML, body: payload)

        if (response) {
            if (response.success) {
                return response.getData()
            } else {
                throw new Exception('REST Service call failed with response code: ' + response.status)
            }
        } else return null;
    }

    Crumb getCrumb(RESTClient client) {
        client.auth.basic(username, password)
        Crumb crumbJson = new Crumb()
        HttpResponseDecorator httpResponse = null;
        try {
            httpResponse = client.get(path: "${url}crumbIssuer/api/json");
        } catch(HttpResponseException e) {
            return crumbJson
        }
        if (httpResponse !=null && httpResponse.isSuccess()) {
            crumbJson.crumbRequestField = httpResponse.data.get("crumbRequestField")
            crumbJson.crumb = httpResponse.data.get("crumb");
        }
        crumbJson
    }

    @Override
    public String getConfiguration(String jobName, Map overrides)
            throws com.terrafolio.gradle.plugins.jenkins.service.JenkinsServiceException {
        def responseXml
        try {
			
            responseXml = restServiceGET(overrides.uri, overrides.params)
        } catch (HttpResponseException hre) {
            responseXml = null
        } catch (Exception e) {
            throw new JenkinsServiceException("Jenkins Service Call failed", e)
        }

        if (responseXml != null) {
            def sbuilder = new StreamingMarkupBuilder()
            return sbuilder.bind { mkp.yield responseXml }.toString()
        } else {
            return null
        }
    }

    @Override
    public void updateConfiguration(String jobName, String configXml,
                                    Map overrides) throws JenkinsServiceException {
        try {
            restServicePOST(overrides.uri, overrides.params, configXml)
        } catch (Exception e) {
            throw new JenkinsServiceException("Jenkins Service Call failed", e)
        }
    }

    @Override
    public void deleteConfiguration(String jobName, Map overrides)
            throws JenkinsServiceException {
        try {
            restServicePOST(overrides.uri, overrides.params, "")
        } catch (Exception e) {
            throw new JenkinsServiceException("Jenkins Service Call failed", e)
        }

    }

    @Override
    public void createConfiguration(String jobName, String configXml, Map overrides)
            throws JenkinsServiceException {
        try {
            restServicePOST(overrides.uri, overrides.params, configXml)
        } catch (Exception e) {
            throw new JenkinsServiceException("Jenkins Service Call failed", e)
        }

    }

}
