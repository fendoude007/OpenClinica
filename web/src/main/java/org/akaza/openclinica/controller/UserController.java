package org.akaza.openclinica.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.PathParam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.annotations.ApiOperation;
import org.akaza.openclinica.bean.core.Role;
import org.akaza.openclinica.bean.login.StudyUserRoleBean;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.login.UserDTO;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.controller.helper.RestfulServiceHelper;
import org.akaza.openclinica.dao.core.CoreResources;
import org.akaza.openclinica.dao.hibernate.StudyDao;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.datamap.StudyEnvEnum;
import org.akaza.openclinica.domain.datamap.StudySubject;
import org.akaza.openclinica.domain.user.UserAccount;
import org.akaza.openclinica.service.*;
import org.akaza.openclinica.service.crfdata.xform.EnketoURLRequest;
import org.akaza.openclinica.web.util.ErrorConstants;
import org.akaza.openclinica.web.util.HeaderUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

/**
 * A simple example of an annotated Spring Controller. Notice that it is a POJO; it
 * does not implement any Spring interfaces or extend Spring classes.
 */
@Controller
@RequestMapping( value = "/auth/api" )
public class UserController {
    //Autowire the class that handles the sidebar structure with a configured
    //bean named "sidebarInit"
    @Autowired
    @Qualifier( "sidebarInit" )
    private SidebarInit sidebarInit;
    private RestfulServiceHelper restfulServiceHelper;

    @Autowired
    @Qualifier( "dataSource" )
    private BasicDataSource dataSource;

    @Autowired
    private UserService userService;

    @Autowired
    private ValidateService validateService;

    @Autowired
    private UtilService utilService;

    @Autowired
    private StudyDao studyDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());
    private static final String ENTITY_NAME = "UserController";

    public UserController() {
    }

    /**
     * The method is mapped to the URL /user.htm
     *
     * @param request The HttpServletRequest for storing attributes.
     * @param userId  The id of the user.
     * @return The return value is a ModelMap (instead of ModelAndView object),
     * because the view name automatically resolves to "user"
     */
    @RequestMapping( "/user" )
    public ModelMap userHandler(HttpServletRequest request,
                                @RequestParam( "id" ) int userId) {
        ModelMap map = new ModelMap();
        List<String> userList = new ArrayList<String>();

        //set up request attributes for sidebar
        setUpSidebar(request);

        userList.add("Bruce");
        userList.add("Yufang");
        userList.add("Krikor");
        userList.add("Tom");


        //TODO: Get user from Hibernate DAO class
        //userList.add(userDao.loadUser(userId).getName())
        map.addAllAttributes(userList);
        return map;
    }

    @RequestMapping( value = "/clinicaldata/studies/{studyOID}/participants/{SSID}/connect", method = RequestMethod.POST )
    public ResponseEntity<OCUserDTO> connectParticipant(HttpServletRequest request, @PathVariable( "studyOID" ) String studyOid, @PathVariable( "SSID" ) String ssid, @RequestBody OCParticipantDTO participantDTO) {
        utilService.setSchemaFromStudyOid(studyOid);
        String accessToken = utilService.getAccessTokenFromRequest(request);
        UserAccountBean ownerUserAccountBean = utilService.getUserAccountFromRequest(request);
        String customerUuid = utilService.getCustomerUuidFromRequest(request);

        OCUserDTO ocUserDTO = userService.connectParticipant(studyOid, ssid, participantDTO, accessToken, ownerUserAccountBean, customerUuid);
        logger.info("REST request to POST OCUserDTO : {}", ocUserDTO);
        return new ResponseEntity<OCUserDTO>(ocUserDTO, HttpStatus.OK);
    }


    @RequestMapping( value = "/clinicaldata/studies/{studyOID}/participants/{SSID}", method = RequestMethod.GET )
    public ResponseEntity<OCUserDTO> getParticipant(HttpServletRequest request, @PathVariable( "studyOID" ) String studyOid, @PathVariable( "SSID" ) String ssid) {
        utilService.setSchemaFromStudyOid(studyOid);
        String accessToken = utilService.getAccessTokenFromRequest(request);

        OCUserDTO ocUserDTO = userService.getParticipantAccount(studyOid, ssid, accessToken);
        logger.info("REST request to GET OCUserDTO : {}", ocUserDTO);
        if (ocUserDTO == null) {
            return new ResponseEntity<OCUserDTO>(ocUserDTO, HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<OCUserDTO>(ocUserDTO, HttpStatus.OK);
        }
    }


    @RequestMapping( value = "/clinicaldata/studies/{studyOID}/participantUsers", method = RequestMethod.GET )
    public ResponseEntity<List<OCUserDTO>> getAllParticipantFromUserService(HttpServletRequest request, @PathVariable( "studyOID" ) String studyOid) {
        utilService.setSchemaFromStudyOid(studyOid);
        String accessToken = utilService.getAccessTokenFromRequest(request);

        List<OCUserDTO> ocUserDTOs = userService.getAllParticipantAccountsFromUserService(accessToken);
        logger.info("REST request to GET List of OCUserDTO : {}", ocUserDTOs);

        return new ResponseEntity<List<OCUserDTO>>(ocUserDTOs, HttpStatus.OK);

    }

    @RequestMapping( value = "/clinicaldata/studies/{studyOID}/participants/{SSID}/accessLink", method = RequestMethod.GET )
    public ResponseEntity<ParticipantAccessDTO> getAccessLink(HttpServletRequest request, @PathVariable( "studyOID" ) String studyOid, @PathVariable( "SSID" ) String ssid) {
        utilService.setSchemaFromStudyOid(studyOid);
        String accessToken = utilService.getAccessTokenFromRequest(request);
        String customerUuid = utilService.getCustomerUuidFromRequest(request);
        UserAccountBean userAccountBean = utilService.getUserAccountFromRequest(request);

        ParticipantAccessDTO participantAccessDTO = userService.getAccessInfo(accessToken, studyOid, ssid, customerUuid, userAccountBean);
        if (participantAccessDTO == null) {
            logger.error("REST request to GET AccessLink Object for Participant not found ");
            return new ResponseEntity<ParticipantAccessDTO>(participantAccessDTO, HttpStatus.NOT_FOUND);
        }

        logger.info("REST request to GET AccessLink Object : {}", participantAccessDTO);
        return new ResponseEntity<ParticipantAccessDTO>(participantAccessDTO, HttpStatus.OK);
    }

    @RequestMapping( value = "/clinicaldata/studies/{studyOID}/participants/searchByFields", method = RequestMethod.GET )
    public ResponseEntity<List<OCUserDTO>> searchByIdentifier(HttpServletRequest request, @PathVariable( "studyOID" ) String studyOid, @PathParam( "participantId" ) String participantId, @PathParam( "firstName" ) String firstName, @PathParam( "lastName" ) String lastName, @PathParam( "identifier" ) String identifier) {
        utilService.setSchemaFromStudyOid(studyOid);
        String accessToken = utilService.getAccessTokenFromRequest(request);
        UserAccountBean userAccountBean = utilService.getUserAccountFromRequest(request);
        List<OCUserDTO> userDTOs = null;
        // validate accessToken against studyOid ,
        // also validate accessToken's user role crc/investigator
        userDTOs = userService.searchParticipantsByFields(studyOid, accessToken, participantId, firstName, lastName, identifier, userAccountBean);

        logger.info("REST request to POST OCUserDTO : {}", userDTOs);
        return new ResponseEntity<List<OCUserDTO>>(userDTOs, HttpStatus.OK);
    }

    @ApiOperation( value = "To extract participants info", notes = "Will extract the data in a text file" )
    @RequestMapping( value = "/clinicaldata/studies/{studyOID}/sites/{siteOID}/participants/extractPartcipantsInfo", method = RequestMethod.GET )
    public ResponseEntity<List<OCUserDTO>> extractPartcipantsInfo(HttpServletRequest request, @PathVariable( "studyOID" ) String studyOid, @PathVariable( "siteOID" ) String siteOid) throws InterruptedException {
        utilService.setSchemaFromStudyOid(studyOid);
        Study tenantStudy = getTenantStudy(studyOid);
        Study tenantSite = getTenantStudy(siteOid);


        if (!validateService.isStudyOidValid(studyOid)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_INVALID_STUDY_OID, "InValid StudyOID. The StudyOID is invalid or does not exist")).body(null);
        }
        if (!validateService.isStudyOidValidStudyLevelOid(studyOid)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_INVALID_STUDY_OID_AS_STUDY, "InValid StudyOID. The StudyOID should have a Study Level Oid")).body(null);
        }
        if (!validateService.isSiteOidValid(siteOid)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_INVALID_SITE_OID, "InValid SiteOID. The SiteOID is invalid or does not exist")).body(null);
        }
        if (!validateService.isSiteOidValidSiteLevelOid(siteOid)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_INVALID_SITE_OID_AS_SITE, "InValid SiteOID. The SiteOID should have a Site Level Oid")).body(null);
        }
        if (!validateService.isStudyToSiteRelationValid(studyOid, siteOid)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_MISMATCH_STUDY_OID_AND_SITE_OID, "Mismatch StudyOID and SiteOID. The StudyOID and SiteOID relation is invalid")).body(null);
        }

        UserAccountBean userAccountBean = utilService.getUserAccountFromRequest(request);
        ArrayList<StudyUserRoleBean> userRoles = userAccountBean.getRoles();

        if (!validateService.isUserHasCrcOrInvestigaterRole(userRoles)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_INCORRECT_USER_ROLE, "Incorrect User Role. The user role should be either a CRC or an Investigator")).body(null);
        }

        if (!validateService.isUserRoleHasAccessToSite(userRoles, siteOid)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_MISMATCH_USER_ROLE_AND_SITE, "Mismatch User Role and Site. The user role does not have permission to access this site")).body(null);
        }


        if (!validateService.isParticipateActive(tenantStudy)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, ErrorConstants.ERR_PARTICIAPTE_INACTIVE, "Participate is Inactive. Participate module for the study is inactive")).body(null);
        }

        String accessToken = utilService.getAccessTokenFromRequest(request);
        String customerUuid = utilService.getCustomerUuidFromRequest(request);

        userService.extractParticipantsInfo(studyOid, siteOid, accessToken, customerUuid, userAccountBean);


        logger.info("REST request to POST OCUserDTO ");
        return new ResponseEntity<List<OCUserDTO>>(HttpStatus.OK);
    }


    private void setUpSidebar(HttpServletRequest request) {
        if (sidebarInit.getAlertsBoxSetup() ==
                SidebarEnumConstants.OPENALERTS) {
            request.setAttribute("alertsBoxSetup", true);
        }

        if (sidebarInit.getInfoBoxSetup() == SidebarEnumConstants.OPENINFO) {
            request.setAttribute("infoBoxSetup", true);
        }
        if (sidebarInit.getInstructionsBoxSetup() == SidebarEnumConstants.OPENINSTRUCTIONS) {
            request.setAttribute("instructionsBoxSetup", true);
        }

        if (!(sidebarInit.getEnableIconsBoxSetup() ==
                SidebarEnumConstants.DISABLEICONS)) {
            request.setAttribute("enableIconsBoxSetup", true);
        }


    }

    public SidebarInit getSidebarInit() {
        return sidebarInit;
    }

    public void setSidebarInit(SidebarInit sidebarInit) {
        this.sidebarInit = sidebarInit;
    }

    private Study getTenantStudy(String studyOid) {
        return studyDao.findByOcOID(studyOid);
    }

    @ApiOperation( value = "To download participant access code", notes = "Will download access code report text file" )
    @RequestMapping( value = "/participants/{filename}/downloadReportFile", method = RequestMethod.GET )
    public void getLogFile(HttpServletRequest request, @PathVariable( "filename" ) String fileName, HttpServletResponse response) throws Exception {

        InputStream inputStream = null;
        try {
            String logFileName = getFilePath() + File.separator + fileName;
            File fileToDownload = new File(logFileName);
            inputStream = new FileInputStream(fileToDownload);
            response.setContentType("application/force-download");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            IOUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            logger.debug("Request could not be completed at this moment. Please try again.");
            logger.debug(e.getStackTrace().toString());
            throw e;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.debug(e.getStackTrace().toString());
                    throw e;
                }
            }
        }
    }

    private String getFilePath() {
        return CoreResources.getField("filePath") + "participants_report_file";
    }


}
