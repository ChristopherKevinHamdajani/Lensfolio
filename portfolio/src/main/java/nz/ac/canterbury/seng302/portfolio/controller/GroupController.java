package nz.ac.canterbury.seng302.portfolio.controller;

import nz.ac.canterbury.seng302.portfolio.model.Group;
import nz.ac.canterbury.seng302.portfolio.model.NotificationMessage;
import nz.ac.canterbury.seng302.portfolio.model.NotificationResponse;
import nz.ac.canterbury.seng302.portfolio.service.ElementService;
import nz.ac.canterbury.seng302.portfolio.service.GroupService;
import nz.ac.canterbury.seng302.portfolio.service.RegisterClientService;
import nz.ac.canterbury.seng302.portfolio.service.UserAccountClientService;
import nz.ac.canterbury.seng302.shared.identityprovider.*;
import nz.ac.canterbury.seng302.shared.util.ValidationError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import javax.servlet.http.HttpServletResponse;

/**
 * Controller for group page
 */
@Controller
public class GroupController {

    @Autowired
    private UserAccountClientService userAccountClientService;

    @Autowired
    private ElementService elementService;

    @Autowired
    public GroupService groupService;

    @Autowired
    RegisterClientService registerClientService;

    private final String updateMessageId = "isUpdateSuccess";


    /**
     * Get method for group page to display group list and group detail
     * @param model  Parameters sent to thymeleaf template to be rendered into HTML
     * @return Group page
     */
    @GetMapping("/groups")
    public String groups(
            Model model,
            @AuthenticationPrincipal AuthState principal
    ) {
        Integer id = userAccountClientService.getUserIDFromAuthState(principal);
        elementService.addHeaderAttributes(model, id);
        model.addAttribute("userId", id);
        UserResponse user = registerClientService.getUserData(id);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userFirstName", user.getFirstName());
        model.addAttribute("userLastName", user.getLastName());

        groupService.addGroupListToModel(model);

        groupService.addToastsToModel(model, 3);

        return "group";
    }

    /**
     * Method to refresh the group table only
     * @param model Parameters sent to thymeleaf template to be rendered into HTML
     * @param groupId id of group to reload
     * @return Group page
     */
    @RequestMapping("/groups/local")
    public String localRefresh(
            Model model,
            @RequestParam("groupId") int groupId)
    {
        groupService.addGroupDetailToModel(model, groupId);
        groupService.addGroupListToModel(model);
        return "group::table_refresh";
    }

    /**
     * Method tries to add and sve the new group to the database
     * @param group group being added
     * @param model Parameters sent to thymeleaf template to be rendered into HTML
     * @param httpServletResponse for adding status codes to
     * @return redirect user to group page
     */
    @PostMapping("/add-group")
    public String addGroup(
            @ModelAttribute("group") Group group,
            Model model,
            HttpServletResponse httpServletResponse
    ) {
        CreateGroupResponse response = groupService.createNewGroup(group.getShortName(), group.getLongName());

        if (response.getIsSuccess()) {
            groupService.addGroupDetailToModel(model, response.getNewGroupId());
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            return "group::groupCard";
        }

        List<ValidationError> errors = response.getValidationErrorsList();
        groupService.addGroupNameErrorsToModel(model, errors);
        httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "fragments/groupModal::groupModalBody";
    }

    /**
     * Submits a request to the identity provider to delete the group with the given id from the database. Adds a
     * variable to the model indicating whether or not this was successful.
     * @param id id of the group to delete
     * @param httpServletResponse for adding status codes to
     */
    @DeleteMapping("/delete-group/{id}")
    @ResponseBody
    public void groupRemove(@PathVariable("id") Integer id,
                              HttpServletResponse httpServletResponse) {
        DeleteGroupResponse response = groupService.deleteGroup(id);
        if (response.getIsSuccess()) {
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /**
     * Tries to save new data to group with given groupId to the database.
     * @param id id of event edited
     * @param group Group data to be updated
     * @param model model to add attributes to for Thyemeleaf to inject into the HTML
     * @param httpServletResponse for adding status codes to
     * @throws IllegalArgumentException if sprint cannot be found from the given ID or if it cannot be saved.
     */
    @PostMapping("/edit-group/{id}")
    public String groupEdit(
            @PathVariable("id") Integer id,
            @ModelAttribute("group") Group group,
            Model model,
            HttpServletResponse httpServletResponse
    ) throws IllegalArgumentException {
        ModifyGroupDetailsResponse response = groupService.editGroupDetails(id, group.getShortName(), group.getLongName());

        if (response.getIsSuccess()) {
            groupService.addGroupDetailToModel(model, id);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            return "group::groupCard";
        }

        List<ValidationError> errors = response.getValidationErrorsList();
        groupService.addGroupNameErrorsToModel(model, errors);
        httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "fragments/groupModal::groupModalBody";
    }

    /**
     * This method maps @MessageMapping endpoint to the @SendTo endpoint. Called when something is sent to
     * the MessageMapping endpoint.
     * @param message NotificationMessage that holds information about the event being updated
     * @return returns an NotificationResponse that holds information about the event being updated.
     */
    @MessageMapping("/editing-group")
    @SendTo("/webSocketGroupsGet/being-edited")
    public NotificationResponse updatingArtefact(NotificationMessage message) {
        int groupId = message.getArtefactId();
        String username = message.getUsername();
        String firstName = message.getUserFirstName();
        String lastName = message.getUserLastName();
        String artefactType = message.getArtefactType();
        long dateOfNotification = Date.from(Instant.now()).toInstant().getEpochSecond();
        return new NotificationResponse(HtmlUtils.htmlEscape(message.getArtefactName()), groupId, username, firstName, lastName, dateOfNotification, artefactType);
    }

    /**
     * This method maps @MessageMapping endpoint to the @SendTo endpoint. Called when something is sent to
     * the MessageMapping endpoint. This is triggered when the user is no longer editing.
     * @param message Information about the editing state.
     * @return Returns the message given.
     */
    @MessageMapping("/stop-editing-group")
    @SendTo("/webSocketGroupsGet/stop-being-edited")
    public NotificationResponse stopUpdatingArtefact(NotificationMessage message) {
        int groupId = message.getArtefactId();
        String username = message.getUsername();
        String firstName = message.getUserFirstName();
        String lastName = message.getUserLastName();
        String artefactType = message.getArtefactType();
        long dateOfNotification = Date.from(Instant.now()).toInstant().getEpochSecond();
        return new NotificationResponse(HtmlUtils.htmlEscape(message.getArtefactName()), groupId, username, firstName, lastName, dateOfNotification, artefactType);
    }

    /**
     * This method maps @MessageMapping endpoint to the @SendTo endpoint. Called when something is sent to
     * the MessageMapping endpoint. This method also triggers some sort of re-render of the events.
     * @param message NotificationMessage that holds information about the event being updated
     * @return returns an NotificationResponse that holds information about the event being updated.
     */
    @MessageMapping("/saved-edited-group")
    @SendTo("/webSocketGroupsGet/save-edit")
    public NotificationResponse savingUpdatedArtefact(NotificationMessage message) {
        int groupId = message.getArtefactId();
        String username = message.getUsername();
        String firstName = message.getUserFirstName();
        String lastName = message.getUserLastName();
        long dateOfNotification = Date.from(Instant.now()).toInstant().getEpochSecond();
        String artefactType = message.getArtefactType();
        NotificationResponse response = new NotificationResponse(HtmlUtils.htmlEscape(message.getArtefactName()), groupId, username, firstName, lastName, dateOfNotification, artefactType);
        // Trigger reload and save the last event's information
        groupService.addNotification(response, 3);
        return response;
    }

}
