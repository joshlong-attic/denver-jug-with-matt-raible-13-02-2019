import React, { Component } from 'react';
import { interval } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';
import { Auth } from './App';

interface Profile {
  id: number;
  email: string;
}

interface ProfileListProps {
  auth: Auth;
}

interface ProfileListState {
  isLoading: boolean;
  profiles: Array<Profile>;
}

class ProfileList extends Component<ProfileListProps, ProfileListState> {

  constructor(props: ProfileListProps) {
    super(props);

    this.state = {
      profiles: [],
      isLoading: false
    };
  }

  async componentDidMount() {
    this.setState({isLoading: true});
    const headers = {
      headers: {Authorization: 'Bearer ' + await this.props.auth.getAccessToken()}
    };

    const response = await fetch('http://localhost:8080/profiles', headers);

    const data = await response.json();
    this.setState({profiles: data, isLoading: false});

    const socket = new WebSocket('ws://localhost:8080/ws/profiles');
    socket.addEventListener('message', async (event: any) => {
      const message = JSON.parse(event.data);
      const request = await fetch(`http://localhost:8080/profiles/${message.id}`, headers);
      const profile = await request.json();
      this.state.profiles.push(profile);
      this.setState({profiles: this.state.profiles});
    });
  }

  render() {
    const {profiles, isLoading} = this.state;

    if (isLoading) {
      return <p>Loading...</p>;
    }

    return (
      <div>
        <h2>Profile List</h2>
        {profiles.map((profile: Profile) =>
          <div key={profile.id}>
            {profile.email}
          </div>
        )}
      </div>
    );
  }
}

export default ProfileList;
