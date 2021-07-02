package sample.resourceserver;

import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.context.annotation.Bean;

import sample.oauthutil.OauthUtil;
import sample.oauthutil.AccessToken;
import sample.oauthutil.IntrospectionResponse;

@RestController
public class ResourceServerController {

	@Autowired
	ResourceServerConfiguration serverConfig;

    @Autowired
    RestTemplate restTemplate;
    
    private void printRequest(String msg, RequestEntity req) {
   	 
     	System.out.println(msg);     	
     	System.out.println(req.getMethod().toString());
     	System.out.println(req.getUrl().toString());
     	System.out.println(" - Headers:\n"+req.getHeaders().toString());
     	if (req.hasBody())
     		System.out.println(" - Body:\n"+req.getBody().toString()+"\n");
     	else 
     		System.out.println("\n");
    	return;
    }
    
	private boolean requestTokenIntrospection(String accessToken) {
		Boolean result = false;

		StringBuilder introspectUrl = new StringBuilder();
		introspectUrl.append(serverConfig.getAuthserverUrl()).append(serverConfig.getIntrospectionEndpoint());

		HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("Authorization", "Basic " + OauthUtil.encodeToBasicClientCredential(serverConfig.getClientId(), serverConfig.getClientSecret()));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<String, String>();
        params.add("token", accessToken);

        RequestEntity<?> req = new RequestEntity<>(params, headers, HttpMethod.POST, URI.create(introspectUrl.toString()));
        IntrospectionResponse response = null;
        printRequest("* Introspection request:",req);
//        System.out.println("Sent Introspection request:"+req.toString()+"\n");

		
        try {
            ResponseEntity<IntrospectionResponse> res = restTemplate.exchange(req, IntrospectionResponse.class);
			response = res.getBody();
        	System.out.println("* Introspection response:\n"+res.getStatusCode()+"\n"+"\"active\":"+response.getActive()+"\n");
			result = response.getActive() == "true";
        } catch (HttpClientErrorException e) {
            System.out.println("!! response code=" + e.getStatusCode()+"\n");
            System.out.println(e.getResponseBodyAsString()+"\n");

        }

		return result;
	}

    @RequestMapping(value = "/echo", method = RequestMethod.GET)
    public ResponseEntity<Map<String, String>> getEcho() {
		return new ResponseEntity<>(Collections.singletonMap("message", "echo!"), HttpStatus.OK);
    }

	@RequestMapping(value = "/readdata", method = RequestMethod.GET)
	public ResponseEntity<Map<String, String>> getReadData(@RequestHeader("Authorization") String authorizationString) {
		String accessToken = (authorizationString.split(" ", 0))[1];

		if(requestTokenIntrospection(accessToken)) {
			return new ResponseEntity<>(Collections.singletonMap("message", "called readdata"), HttpStatus.OK);
		}
		else {
			return new ResponseEntity<>(Collections.singletonMap("message", "error!"), HttpStatus.UNAUTHORIZED);
		}
	}

	@RequestMapping(value = "/writedata", method = RequestMethod.GET)
	public ResponseEntity<Map<String, String>> getWriteData(@RequestHeader("Authorization") String authorizationString) {
		String accessToken = (authorizationString.split(" ", 0))[1];

		if (requestTokenIntrospection(accessToken)) {
			AccessToken token = OauthUtil.readJsonContent(OauthUtil.decodeFromBase64Url(accessToken), AccessToken.class);
			System.out.println("Scope of Token:"+token.getScope()+"\n");
			if (token.getScopeList().contains("writedata")) {
				return new ResponseEntity<>(Collections.singletonMap("message", "called writedata"), HttpStatus.OK);
			}
			else {
				System.out.println("Error: writedata scope is not included.");
				return new ResponseEntity<>(Collections.singletonMap("message", "error!"), HttpStatus.FORBIDDEN);
			}
		}
		else {
			return new ResponseEntity<>(Collections.singletonMap("message", "error!"), HttpStatus.UNAUTHORIZED);
		}
	}

    @Bean
    public RestTemplate restTemplate() {
        RestTemplateBuilder RestTemplateBuilder = new RestTemplateBuilder();
        return RestTemplateBuilder.build();
    }
}