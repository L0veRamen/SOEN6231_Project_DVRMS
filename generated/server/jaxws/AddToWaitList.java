
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "addToWaitList", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "addToWaitList", namespace = "http://server/", propOrder = {
    "customerID",
    "vehicleID",
    "startDate",
    "endDate"
})
public class AddToWaitList {

    @XmlElement(name = "customerID", namespace = "")
    private String customerID;
    @XmlElement(name = "vehicleID", namespace = "")
    private String vehicleID;
    @XmlElement(name = "startDate", namespace = "")
    private String startDate;
    @XmlElement(name = "endDate", namespace = "")
    private String endDate;

    /**
     * 
     * @return
     *     returns String
     */
    public String getCustomerID() {
        return this.customerID;
    }

    /**
     * 
     * @param customerID
     *     the value for the customerID property
     */
    public void setCustomerID(String customerID) {
        this.customerID = customerID;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getVehicleID() {
        return this.vehicleID;
    }

    /**
     * 
     * @param vehicleID
     *     the value for the vehicleID property
     */
    public void setVehicleID(String vehicleID) {
        this.vehicleID = vehicleID;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getStartDate() {
        return this.startDate;
    }

    /**
     * 
     * @param startDate
     *     the value for the startDate property
     */
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getEndDate() {
        return this.endDate;
    }

    /**
     * 
     * @param endDate
     *     the value for the endDate property
     */
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

}
